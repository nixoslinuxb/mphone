package com.mphone.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.mphone.model.Contact
import com.mphone.model.SmsMessage
import com.mphone.model.SmsThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsRepository(private val context: Context) {
    private val _threads = MutableStateFlow<List<SmsThread>>(emptyList())
    val threads: StateFlow<List<SmsThread>> = _threads.asStateFlow()

    private val _spamThreads = MutableStateFlow<List<SmsThread>>(emptyList())
    val spamThreads: StateFlow<List<SmsThread>> = _spamThreads.asStateFlow()

    private val rateLimiter = SmsRateLimiter()

    private val prefs = context.getSharedPreferences("mphone_deleted_sms_prefs", Context.MODE_PRIVATE)

    // SMS loading touches the Telephony provider and the disk cache, so it must
    // never run on the main thread. Loads are also coalesced: a burst of
    // ContentObserver callbacks results in a single (latest) reload.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val persistDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val loadLock = Any()
    private var loadJob: Job? = null
    private var pendingContacts: List<Contact>? = null

    init {
        val cached = SmsLocalCache.loadThreads(context)
        if (cached.isNotEmpty()) {
            val deletedKeys = getDeletedKeys()
            _threads.value = cached.filter {
                !deletedKeys.contains(ContactMerger.normalizePhoneNumber(it.contactNumber))
            }
        }
    }

    fun getThreadKey(number: String, id: String = ""): String {
        val cleanNum = ContactMerger.normalizePhoneNumber(number)
        if (cleanNum.isNotBlank()) return cleanNum
        val cleanName = number.replace(Regex("[^a-zA-Z0-9]"), "").lowercase().trim()
        if (cleanName.isNotBlank()) return cleanName
        return id.replace(Regex("[^a-zA-Z0-9]"), "").lowercase().trim()
    }

    private fun persistThreadsState(threadsList: List<SmsThread>) {
        // Disk writes are serialized on a single background thread. Writing the
        // whole JSON cache on every state change used to block the UI thread.
        val snapshot = threadsList
        ioScope.launch(persistDispatcher) {
            SmsLocalCache.saveThreads(context, snapshot)
        }
    }

    private fun getDeletedKeys(): Set<String> {
        return prefs.getStringSet("deleted_keys", emptySet()) ?: emptySet()
    }

    private fun markKeyDeleted(key: String) {
        if (key.isBlank()) return
        val current = getDeletedKeys().toMutableSet()
        current.add(key)
        prefs.edit().putStringSet("deleted_keys", current).apply()
    }

    fun markKeyRestored(key: String) {
        if (key.isBlank()) return
        try {
            val cleanKey = ContactMerger.normalizePhoneNumber(key)
            val digitsOnly = key.replace(Regex("[^0-9]"), "")
            val short7 = if (cleanKey.length >= 7) cleanKey.takeLast(7) else ""

            val current = getDeletedKeys().toMutableSet()
            current.remove(key)
            current.remove(cleanKey)
            current.remove(digitsOnly)
            if (short7.isNotBlank()) {
                current.removeAll { it.length >= 7 && it.takeLast(7) == short7 }
            }
            prefs.edit().putStringSet("deleted_keys", current).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun markThreadAsRead(threadId: String) {
        val target = _threads.value.find { it.id == threadId }
        val targetNumber = target?.contactNumber ?: threadId
        val cleanKey = ContactMerger.normalizePhoneNumber(targetNumber).ifBlank { threadId }

        _threads.value = _threads.value.map { thread ->
            if (thread.id == threadId || ContactMerger.normalizePhoneNumber(thread.contactNumber) == cleanKey) {
                thread.copy(unreadCount = 0)
            } else {
                thread
            }
        }
        persistThreadsState(_threads.value)

        // Mark read = 1 in system Telephony provider
        try {
            if (cleanKey.isNotBlank()) {
                val values = ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                }
                context.contentResolver.update(
                    Telephony.Sms.CONTENT_URI,
                    values,
                    "address LIKE ? AND read = 0",
                    arrayOf("%$cleanKey%")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun unblockSpamThread(threadId: String) {
        val target = _spamThreads.value.find { it.id == threadId }
        if (target != null) {
            val cleanKey = ContactMerger.normalizePhoneNumber(target.contactNumber)
            markKeyRestored(cleanKey)
            _spamThreads.value = _spamThreads.value.filter { it.id != threadId }
            _threads.value = (listOf(target) + _threads.value).sortedByDescending { it.lastTimestampMillis }
            persistThreadsState(_threads.value)
        }
    }

    fun blockThread(threadId: String) {
        val target = _threads.value.find { it.id == threadId }
        if (target != null) {
            _threads.value = _threads.value.filter { it.id != threadId }
            _spamThreads.value = (listOf(target) + _spamThreads.value).distinctBy { it.id }
            persistThreadsState(_threads.value)
        }
    }

    fun deleteThread(threadId: String) {
        val target = _threads.value.find { it.id == threadId } ?: _spamThreads.value.find { it.id == threadId }
        val targetNumber = target?.contactNumber ?: threadId
        val cleanKey = ContactMerger.normalizePhoneNumber(targetNumber).ifBlank { threadId }

        markKeyDeleted(cleanKey)

        _threads.value = _threads.value.filter {
            it.id != threadId && ContactMerger.normalizePhoneNumber(it.contactNumber) != cleanKey
        }
        _spamThreads.value = _spamThreads.value.filter {
            it.id != threadId && ContactMerger.normalizePhoneNumber(it.contactNumber) != cleanKey
        }

        persistThreadsState(_threads.value)

        // Delete from System Telephony Content Provider
        try {
            if (cleanKey.isNotBlank()) {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "address LIKE ?",
                    arrayOf("%$cleanKey%")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDeletedMessageIds(): Set<String> {
        return try {
            prefs.getStringSet("deleted_message_ids", emptySet()) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun sanitizeSmsBody(body: String): String {
        if (body.isBlank()) return ""
        return body.replace("\r", "").replace("\u00A0", " ").trim()
    }

    fun isMessageDeleted(msg: SmsMessage): Boolean {
        val deletedSet = getDeletedMessageIds()
        if (deletedSet.isEmpty()) return false

        val mId = msg.id.trim()
        val mBody = sanitizeSmsBody(msg.body)
        val mTimestamp = msg.timestamp.trim()
        val threadKey = getThreadKey(msg.threadId)

        // A message is only "deleted" when this exact message (or a copy of it
        // with the same thread/body/time) was deleted. Matching on the body
        // alone used to wipe every message that happened to share the text.
        if (mId.isNotBlank() && deletedSet.contains(mId)) return true
        if (mId.isNotBlank() && deletedSet.contains("id|$mId")) return true
        if (mBody.isNotBlank() && mTimestamp.isNotBlank()) {
            if (deletedSet.contains("msg|$threadKey|$mBody|$mTimestamp")) return true
        }

        return false
    }

    private fun deduplicateSmsMessages(msgs: List<SmsMessage>): List<SmsMessage> {
        if (msgs.isEmpty()) return emptyList()
        return msgs.filterNot { isMessageDeleted(it) }.distinctBy { msg ->
            val cleanB = sanitizeSmsBody(msg.body).lowercase()
            // Include the timestamp so the same text sent twice at different
            // times is preserved as two separate messages.
            "${cleanB}_${msg.isFromUser}_${msg.timestamp.trim()}"
        }
    }

    private fun markMessageDeletedLocally(messageId: String) {
        if (messageId.isBlank()) return
        try {
            val set = getDeletedMessageIds().toMutableSet()
            set.add(messageId)
            prefs.edit().putStringSet("deleted_message_ids", set).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteSingleMessage(threadId: String, message: SmsMessage) {
        val cleanMsgId = message.id.trim()
        val cleanBody = sanitizeSmsBody(message.body)
        val cleanTimestamp = message.timestamp.trim()
        val threadKey = getThreadKey(message.threadId.ifBlank { threadId })

        if (cleanMsgId.isBlank() && cleanBody.isBlank()) return

        // 1. Record fingerprints locally. Keys are scoped to this exact message
        // so deleting one message never removes other messages with same text.
        if (cleanMsgId.isNotBlank()) {
            markMessageDeletedLocally("id|$cleanMsgId")
        }
        val isProviderId = cleanMsgId.isNotBlank() && cleanMsgId.all { it.isDigit() } && cleanMsgId.length < 10
        if (!isProviderId && cleanBody.isNotBlank() && cleanTimestamp.isNotBlank()) {
            markMessageDeletedLocally("msg|$threadKey|$cleanBody|$cleanTimestamp")
            if (threadId.isNotBlank() && threadId != message.threadId) {
                markMessageDeletedLocally("msg|${getThreadKey(threadId)}|$cleanBody|$cleanTimestamp")
            }
        }

        // 2. Delete this single row from the system Telephony provider. Never
        // use a body LIKE match here: it deletes every copy of that text.
        try {
            if (isProviderId) {
                context.contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "_id = ?",
                    arrayOf(cleanMsgId)
                )
            } else if (cleanBody.isNotBlank()) {
                // Locally generated id (timestamp): find the matching provider
                // copy and delete only that one row.
                val threadNumber = _threads.value.find { it.id == threadId }?.contactNumber
                    ?: _spamThreads.value.find { it.id == threadId }?.contactNumber
                val address = message.senderNumber.takeIf {
                    it.isNotBlank() && it != "Me" && it != "Unknown"
                } ?: threadNumber ?: threadId
                val rowId = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID),
                    "address = ? AND body = ?",
                    arrayOf(address, message.body),
                    "date DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                if (!rowId.isNullOrBlank()) {
                    context.contentResolver.delete(
                        Telephony.Sms.CONTENT_URI,
                        "_id = ?",
                        arrayOf(rowId)
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Remove only the target message from in-memory state.
        fun updateThreadList(list: List<SmsThread>): List<SmsThread> {
            return list.map { thread ->
                val remainingMsgs = thread.messages.filterNot { msg ->
                    (cleanMsgId.isNotBlank() && msg.id == cleanMsgId && msg.timestamp == message.timestamp) ||
                    (msg.id == message.id && msg.timestamp == message.timestamp && msg.body == message.body) ||
                    isMessageDeleted(msg)
                }
                val lastMsg = remainingMsgs.lastOrNull()
                thread.copy(
                    lastMessage = lastMsg?.body ?: "",
                    timestamp = lastMsg?.timestamp ?: "",
                    messages = remainingMsgs
                )
            }
        }

        val updatedThreads = updateThreadList(_threads.value)
        val updatedSpam = updateThreadList(_spamThreads.value)

        _threads.value = updatedThreads
        _spamThreads.value = updatedSpam
        persistThreadsState(updatedThreads)
    }

    fun loadSmsThreads(contacts: List<Contact> = emptyList()) {
        synchronized(loadLock) {
            pendingContacts = contacts
            if (loadJob?.isActive != true) {
                loadJob = ioScope.launch {
                    while (true) {
                        val next = synchronized(loadLock) {
                            pendingContacts.also { pendingContacts = null }
                        }
                        if (next != null) {
                            loadSmsThreadsInternal(next)
                            continue
                        }
                        val hasMore = synchronized(loadLock) {
                            if (pendingContacts != null) {
                                true
                            } else {
                                loadJob = null
                                false
                            }
                        }
                        if (!hasMore) break
                    }
                }
            }
        }
    }

    private fun loadSmsThreadsInternal(contacts: List<Contact> = emptyList()) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        val deletedKeys = getDeletedKeys()

        if (!hasPermission) {
            val cached = SmsLocalCache.loadThreads(context)
            val combined = (cached + _threads.value).distinctBy {
                ContactMerger.normalizePhoneNumber(it.contactNumber).ifBlank { it.id }
            }.filter {
                !deletedKeys.contains(ContactMerger.normalizePhoneNumber(it.contactNumber)) && !deletedKeys.contains(it.id)
            }
            if (combined.isNotEmpty()) {
                _threads.value = combined
            } else {
                _threads.value = MockData.sampleSmsThreads.filter {
                    !deletedKeys.contains(ContactMerger.normalizePhoneNumber(it.contactNumber))
                }
            }
            return
        }

        try {
            val list = mutableListOf<SmsThread>()
            val spamList = mutableListOf<SmsThread>()
            val dateFormat = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
            val personThreadsMap = mutableMapOf<String, MutableList<Pair<Long, SmsMessage>>>()
            val personInfoMap = mutableMapOf<String, Pair<String, String>>()
            val numbersMap = mutableMapOf<String, String>()

            // Query the unified SMS table once. Querying the same rows through
            // CONTENT_URI + Inbox + Sent tripled the work and produced duplicates.
            val contactCache = mutableMapOf<String, Contact?>()
            val corporateCache = mutableMapOf<String, String?>()
            fun resolveContact(number: String): Contact? =
                contactCache.getOrPut(number) { ContactMerger.findContactForNumber(contacts, number) }
            fun resolveCorporate(number: String): String? =
                corporateCache.getOrPut(number) { CorporateCallerIdRegistry.getCorporateName(number) }

            try {
                val projection = arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE
                )
                val cursor = context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "date DESC"
                )

                cursor?.use {
                    val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                    val threadIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                    val addrIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                    val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                    val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                    val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)

                    var count = 0
                    while (it.moveToNext() && count < 1000) {
                        count++
                        val id = if (idIdx >= 0) it.getString(idIdx) else ""
                        val threadId = if (threadIdx >= 0) it.getString(threadIdx) ?: "1" else "1"
                        val rawAddress = if (addrIdx >= 0) it.getString(addrIdx) ?: "" else ""
                        val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                        val dateMillis = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                        val typeInt = if (typeIdx >= 0) it.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX

                        val isFromUser = (typeInt == Telephony.Sms.MESSAGE_TYPE_SENT || typeInt == Telephony.Sms.MESSAGE_TYPE_OUTBOX || typeInt == Telephony.Sms.MESSAGE_TYPE_QUEUED)
                        val dateStr = dateFormat.format(Date(dateMillis))

                        if (rawAddress.isNotBlank() && rawAddress != "Unknown" && rawAddress != "Me") {
                            numbersMap[threadId] = rawAddress
                        }

                        val senderNumber = numbersMap[threadId] ?: rawAddress.ifBlank { "Unknown" }
                        val cleanNum = ContactMerger.normalizePhoneNumber(senderNumber)

                        if (cleanNum.isNotBlank() && deletedKeys.contains(cleanNum)) {
                            continue
                        }

                        val matchedContact = resolveContact(senderNumber)
                        val corporateName = resolveCorporate(senderNumber)
                        val personKey = matchedContact?.id ?: cleanNum.ifBlank { senderNumber.ifBlank { threadId } }
                        val displayName = matchedContact?.name ?: corporateName ?: senderNumber

                        if (senderNumber.isNotBlank() && senderNumber != "Unknown") {
                            personInfoMap[personKey] = Pair(displayName, senderNumber)
                        }

                        val message = SmsMessage(
                            id = id.ifBlank { System.currentTimeMillis().toString() },
                            threadId = personKey,
                            senderName = if (isFromUser) "Me" else displayName,
                            senderNumber = senderNumber,
                            body = body,
                            timestamp = dateStr,
                            isFromUser = isFromUser
                        )

                        val threadMsgs = personThreadsMap.getOrPut(personKey) { mutableListOf() }
                        threadMsgs.add(Pair(dateMillis, message))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            var colorIndex = 0
            personThreadsMap.forEach { (personKey, timeAndMsgs) ->
                val sortedPairs = timeAndMsgs.sortedBy { it.first }
                val sortedMsgs = sortedPairs.map { it.second }
                    .filterNot { isMessageDeleted(it) }
                    .distinctBy { "${sanitizeSmsBody(it.body)}_${it.isFromUser}_${it.timestamp}" }
                val lastPair = sortedPairs.lastOrNull()
                val lastMsg = sortedMsgs.lastOrNull()
                val info = personInfoMap[personKey]

                val actualNumber = if (info != null && info.second.isNotBlank() && info.second != "Unknown") {
                    info.second
                } else {
                    sortedMsgs.firstOrNull { it.senderNumber.isNotBlank() && it.senderNumber != "Me" && it.senderNumber != "Unknown" }?.senderNumber ?: personKey
                }

                val cleanActualNum = ContactMerger.normalizePhoneNumber(actualNumber)
                if (deletedKeys.contains(cleanActualNum)) {
                    return@forEach
                }

                val matchedContact = ContactMerger.findContactForNumber(contacts, actualNumber)
                val corporateName = CorporateCallerIdRegistry.getCorporateName(actualNumber)
                val finalName = matchedContact?.name ?: corporateName ?: actualNumber

                val isForeign = matchedContact == null && CountryCodeFilter.isFromOutsideTurkey(actualNumber)
                val isSpam = matchedContact == null && TurkeySpamNumberBlocker.isSpamSms(actualNumber, lastMsg?.body ?: "")

                val threadObj = SmsThread(
                    id = personKey,
                    contactName = if (finalName.isNotBlank() && finalName != "Unknown") finalName else actualNumber,
                    contactNumber = actualNumber,
                    lastMessage = lastMsg?.body ?: "",
                    timestamp = lastMsg?.timestamp ?: "Recent",
                    lastTimestampMillis = lastPair?.first ?: System.currentTimeMillis(),
                    unreadCount = 0,
                    avatarColorIndex = (colorIndex++) % 6,
                    photoUri = matchedContact?.photoUri,
                    messages = sortedMsgs
                )

                if (isForeign || isSpam) {
                    spamList.add(threadObj)
                } else if (actualNumber.isNotBlank() || sortedMsgs.isNotEmpty()) {
                    list.add(threadObj)
                }
            }

            _spamThreads.value = spamList

            // Merge system list with existing in-memory local threads + local disk cache
            val cachedDiskThreads = SmsLocalCache.loadThreads(context)
            val currentLocalThreads = (cachedDiskThreads + _threads.value).distinctBy {
                getThreadKey(it.contactNumber, it.id)
            }
            val mergedThreadsMap = mutableMapOf<String, SmsThread>()

            list.forEach {
                val cleanKey = getThreadKey(it.contactNumber, it.id)
                if (!deletedKeys.contains(cleanKey) && !deletedKeys.contains(it.id)) {
                    mergedThreadsMap[cleanKey] = it
                }
            }

            currentLocalThreads.forEach { localThread ->
                val cleanKey = getThreadKey(localThread.contactNumber, localThread.id)

                val hasIncomingUnread = localThread.unreadCount > 0 || localThread.messages.any { !it.isFromUser }
                if (hasIncomingUnread && (deletedKeys.contains(cleanKey) || deletedKeys.contains(localThread.id))) {
                    markKeyRestored(cleanKey)
                    markKeyRestored(localThread.id)
                    markKeyRestored(localThread.contactNumber)
                }

                val isDeleted = !hasIncomingUnread && (deletedKeys.contains(cleanKey) || deletedKeys.contains(localThread.id))
                if (isDeleted) {
                    return@forEach
                }
                val matched = ContactMerger.findContactForNumber(contacts, localThread.contactNumber)
                val resolvedName = matched?.name ?: localThread.contactName

                val existing = mergedThreadsMap[cleanKey]
                if (existing != null) {
                    val combinedMsgs = deduplicateSmsMessages(localThread.messages + existing.messages)
                    val newestTime = maxOf(existing.lastTimestampMillis, localThread.lastTimestampMillis)
                    val newestMsg = (localThread.messages + existing.messages).maxByOrNull { it.id.toLongOrNull() ?: 0L }
                    mergedThreadsMap[cleanKey] = existing.copy(
                        contactName = matched?.name ?: if (existing.contactName != existing.contactNumber) existing.contactName else localThread.contactName,
                        photoUri = if (matched != null) matched.photoUri else (existing.photoUri ?: localThread.photoUri),
                        lastMessage = newestMsg?.body ?: if (localThread.lastTimestampMillis >= existing.lastTimestampMillis) localThread.lastMessage else existing.lastMessage,
                        timestamp = newestMsg?.timestamp ?: if (localThread.lastTimestampMillis >= existing.lastTimestampMillis) localThread.timestamp else existing.timestamp,
                        lastTimestampMillis = newestTime,
                        unreadCount = maxOf(existing.unreadCount, localThread.unreadCount),
                        messages = combinedMsgs
                    )
                } else {
                    mergedThreadsMap[cleanKey] = localThread.copy(
                        contactName = resolvedName,
                        photoUri = if (matched != null) matched.photoUri else localThread.photoUri,
                        // A local-only thread must respect deletions too, otherwise
                        // deleted messages reappear on the next reload.
                        messages = deduplicateSmsMessages(localThread.messages)
                    )
                }
            }

            val finalThreads = mergedThreadsMap.values.toList().sortedByDescending { it.lastTimestampMillis }
            _threads.value = finalThreads
            persistThreadsState(_threads.value)
        } catch (e: Exception) {
            e.printStackTrace()
            val deletedKeys = getDeletedKeys()
            _threads.value = _threads.value.filter {
                !deletedKeys.contains(ContactMerger.normalizePhoneNumber(it.contactNumber))
            }
        }
    }

    private fun resolveContactName(context: Context, number: String): String {
        if (number.isBlank() || number == "Unknown") return "Unknown"
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return number
    }

    fun sendMessage(threadId: String, text: String, subId: Int? = null, contactsList: List<Contact> = emptyList()): SendStatus {
        val cleanKey = ContactMerger.normalizePhoneNumber(threadId)
        val targetThread = _threads.value.find { thread ->
            thread.id == threadId ||
                    (cleanKey.isNotBlank() && ContactMerger.normalizePhoneNumber(thread.contactNumber) == cleanKey) ||
                    thread.contactName.equals(threadId, ignoreCase = true)
        }

        val matchedContact = ContactMerger.findContactForNumber(contactsList, threadId)
            ?: contactsList.find { it.name.equals(threadId, ignoreCase = true) }

        val destinationNumber = targetThread?.contactNumber?.ifBlank { null }
            ?: matchedContact?.phoneNumber
            ?: threadId

        val finalCleanNum = ContactMerger.normalizePhoneNumber(destinationNumber)
        if (finalCleanNum.isBlank() && destinationNumber.isBlank()) {
            return SendStatus.RateLimited("Invalid destination number", 10)
        }

        // Brand / alphanumeric sender IDs cannot receive SMS replies.
        if (SmsSenderId.isBrandSenderId(destinationNumber)) {
            return SendStatus.BrandBlocked("Marka mesajlarına yanıt gönderilemez.")
        }

        markKeyRestored(finalCleanNum)

        val check = rateLimiter.checkCanSend(destinationNumber, text)
        if (check != SendStatus.Success) {
            return check
        }

        rateLimiter.recordSend(destinationNumber, text)

        val timeFormat = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
        val nowMillis = System.currentTimeMillis()
        val nowStr = timeFormat.format(Date(nowMillis))

        // 1. Dispatch via System SmsManager
        sendSystemSms(destinationNumber, text, subId)

        // 2. Update local state with new sent message
        val newMsg = SmsMessage(
            id = nowMillis.toString(),
            threadId = targetThread?.id ?: threadId,
            senderName = "Me",
            senderNumber = "Me",
            body = text,
            timestamp = nowStr,
            isFromUser = true
        )

        var threadFound = false
        val updatedThreads = _threads.value.map { thread ->
            val isMatch = thread.id == threadId ||
                    (targetThread != null && thread.id == targetThread.id) ||
                    (finalCleanNum.isNotBlank() && ContactMerger.normalizePhoneNumber(thread.contactNumber) == finalCleanNum) ||
                    thread.contactName.equals(threadId, ignoreCase = true)

            if (isMatch) {
                threadFound = true
                val combinedMsgs = thread.messages + newMsg
                thread.copy(
                    lastMessage = text,
                    timestamp = nowStr,
                    lastTimestampMillis = nowMillis,
                    messages = combinedMsgs
                )
            } else {
                thread
            }
        }.sortedByDescending { it.lastTimestampMillis }

        if (threadFound) {
            _threads.value = updatedThreads
        } else {
            val displayName = matchedContact?.name ?: if (destinationNumber == finalCleanNum) destinationNumber else threadId
            val fallbackThread = SmsThread(
                id = threadId,
                contactName = displayName,
                contactNumber = destinationNumber,
                lastMessage = text,
                timestamp = nowStr,
                lastTimestampMillis = nowMillis,
                unreadCount = 0,
                avatarColorIndex = (0..5).random(),
                messages = listOf(newMsg)
            )
            _threads.value = (listOf(fallbackThread) + _threads.value).sortedByDescending { it.lastTimestampMillis }
        }

        persistThreadsState(_threads.value)
        return SendStatus.Success
    }

    fun createThread(recipientName: String, recipientNumber: String, firstMessage: String? = null): Pair<SendStatus, String?> {
        val cleanTarget = ContactMerger.normalizePhoneNumber(recipientNumber)
        markKeyRestored(cleanTarget)

        val existingThread = _threads.value.find {
            (cleanTarget.isNotBlank() && ContactMerger.normalizePhoneNumber(it.contactNumber) == cleanTarget) ||
            (it.contactName.isNotBlank() && it.contactName.equals(recipientName, ignoreCase = true))
        }

        val targetThreadId = existingThread?.id ?: cleanTarget.ifBlank { recipientNumber }

        if (firstMessage.isNullOrBlank()) {
            if (existingThread != null) {
                return Pair(SendStatus.Success, existingThread.id)
            } else {
                val newThread = SmsThread(
                    id = targetThreadId,
                    contactName = recipientName.ifBlank { recipientNumber },
                    contactNumber = recipientNumber,
                    lastMessage = "",
                    timestamp = "Now",
                    lastTimestampMillis = System.currentTimeMillis(),
                    unreadCount = 0,
                    avatarColorIndex = (0..5).random(),
                    messages = emptyList()
                )
                _threads.value = listOf(newThread) + _threads.value
                persistThreadsState(_threads.value)
                return Pair(SendStatus.Success, targetThreadId)
            }
        }

        val check = rateLimiter.checkCanSend(recipientNumber, firstMessage)
        if (check != SendStatus.Success) {
            return Pair(check, null)
        }

        if (existingThread != null) {
            val status = sendMessage(existingThread.id, firstMessage)
            return Pair(status, if (status == SendStatus.Success) existingThread.id else null)
        }

        rateLimiter.recordSend(recipientNumber, firstMessage)

        val timeFormat = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
        val nowMillis = System.currentTimeMillis()
        val nowStr = timeFormat.format(Date(nowMillis))

        // 1. Dispatch via System SmsManager
        sendSystemSms(recipientNumber, firstMessage)

        // 2. Create new thread in local state
        val firstMsg = SmsMessage(
            id = nowMillis.toString(),
            threadId = targetThreadId,
            senderName = "Me",
            senderNumber = "Me",
            body = firstMessage,
            timestamp = nowStr,
            isFromUser = true
        )

        val newThread = SmsThread(
            id = targetThreadId,
            contactName = recipientName.ifBlank { recipientNumber },
            contactNumber = recipientNumber,
            lastMessage = firstMessage,
            timestamp = nowStr,
            lastTimestampMillis = nowMillis,
            unreadCount = 0,
            avatarColorIndex = (0..5).random(),
            messages = listOf(firstMsg)
        )

        _threads.value = (listOf(newThread) + _threads.value).sortedByDescending { it.lastTimestampMillis }
        persistThreadsState(_threads.value)
        return Pair(SendStatus.Success, targetThreadId)
    }

    fun receiveIncomingSms(sender: String, body: String, contacts: List<Contact> = emptyList()) {
        val cleanSender = ContactMerger.normalizePhoneNumber(sender)
        val matchedContact = ContactMerger.findContactForNumber(contacts, sender)

        markKeyRestored(cleanSender)
        markKeyRestored(sender)
        if (matchedContact != null) {
            markKeyRestored(matchedContact.id)
        }

        val displayName = matchedContact?.name ?: resolveContactName(context, sender)

        // Saved contacts are NEVER filtered as spam!
        val isSpam = matchedContact == null && (
            CountryCodeFilter.isFromOutsideTurkey(sender) ||
            TurkeySpamNumberBlocker.isSpamSms(sender, body)
        )

        val timeFormat = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
        val nowMillis = System.currentTimeMillis()
        val nowStr = timeFormat.format(Date(nowMillis))

        if (isSpam) {
            val newMsg = SmsMessage(
                id = nowMillis.toString(),
                threadId = "spam_$cleanSender",
                senderName = displayName,
                senderNumber = sender,
                body = body,
                timestamp = nowStr,
                isFromUser = false
            )
            val spamThread = SmsThread(
                id = "spam_$cleanSender",
                contactName = displayName,
                contactNumber = sender,
                lastMessage = body,
                timestamp = nowStr,
                lastTimestampMillis = nowMillis,
                unreadCount = 1,
                avatarColorIndex = (0..5).random(),
                messages = listOf(newMsg)
            )
            _spamThreads.value = (listOf(spamThread) + _spamThreads.value).distinctBy { it.contactNumber }
            return
        }

        val cleanKey = getThreadKey(sender)
        val existingThread = _threads.value.find {
            getThreadKey(it.contactNumber, it.id) == cleanKey ||
                    (cleanSender.isNotBlank() && ContactMerger.normalizePhoneNumber(it.contactNumber) == cleanSender) ||
                    (it.contactName.isNotBlank() && it.contactName.equals(sender, ignoreCase = true))
        }

        if (existingThread != null) {
            // Deduplicate: If identical message body arrived in the last 5 seconds, ignore duplicate!
            val isDuplicate = existingThread.messages.any {
                it.body.trim() == body.trim() && !it.isFromUser && (nowMillis - (it.id.toLongOrNull() ?: 0L)) < 5000L
            }
            if (isDuplicate) return

            val newMsg = SmsMessage(
                id = nowMillis.toString(),
                threadId = existingThread.id,
                senderName = displayName,
                senderNumber = sender,
                body = body,
                timestamp = nowStr,
                isFromUser = false
            )

            val combinedMsgs = existingThread.messages + newMsg

            _threads.value = _threads.value.map { thread ->
                if (thread.id == existingThread.id) {
                    thread.copy(
                        contactName = displayName,
                        lastMessage = body,
                        timestamp = nowStr,
                        lastTimestampMillis = nowMillis,
                        unreadCount = thread.unreadCount + 1,
                        messages = combinedMsgs
                    )
                } else {
                    thread
                }
            }.sortedByDescending { it.lastTimestampMillis }
        } else {
            val newThreadId = cleanSender.ifBlank { sender }
            val newMsg = SmsMessage(
                id = nowMillis.toString(),
                threadId = newThreadId,
                senderName = displayName,
                senderNumber = sender,
                body = body,
                timestamp = nowStr,
                isFromUser = false
            )
            val newThread = SmsThread(
                id = newThreadId,
                contactName = displayName,
                contactNumber = sender,
                lastMessage = body,
                timestamp = nowStr,
                lastTimestampMillis = nowMillis,
                unreadCount = 1,
                avatarColorIndex = (0..5).random(),
                messages = listOf(newMsg)
            )
            _threads.value = (listOf(newThread) + _threads.value).sortedByDescending { it.lastTimestampMillis }
        }

        persistThreadsState(_threads.value)
    }

    private fun getSim1SubscriptionId(context: Context): Int? {
        try {
            val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPhoneStatePermission) {
                val subManager = context.getSystemService(SubscriptionManager::class.java)
                val activeSubs = subManager?.activeSubscriptionInfoList
                if (!activeSubs.isNullOrEmpty()) {
                    // Pick SIM 1 (simSlotIndex == 0 or minimum slot index)
                    val sim1Sub = activeSubs.minByOrNull { it.simSlotIndex }
                    if (sim1Sub != null) {
                        return sim1Sub.subscriptionId
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun sendSystemSms(destinationNumber: String, text: String, subId: Int? = null) {
        val hasSendPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSendPermission && destinationNumber.isNotBlank() && destinationNumber != "Me") {
            try {
                // If subId is not explicitly specified, default to SIM 1
                val targetSubId = if (subId != null && subId != -1) {
                    subId
                } else {
                    getSim1SubscriptionId(context)
                }

                val smsManager = if (targetSubId != null && targetSubId != -1) {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(targetSubId)
                } else {
                    context.getSystemService(SmsManager::class.java)
                }

                val parts = smsManager?.divideMessage(text)
                if (parts != null && parts.size > 1) {
                    smsManager.sendMultipartTextMessage(destinationNumber, null, parts, null, null)
                } else {
                    smsManager?.sendTextMessage(destinationNumber, null, text, null, null)
                }

                // Insert sent message into system Telephony Provider content://sms/sent
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, destinationNumber)
                    put(Telephony.Sms.BODY, text)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    put(Telephony.Sms.READ, 1)
                }
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
