package com.mphone.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.mphone.model.CallLogItem
import com.mphone.model.CallType
import com.mphone.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogRepository(private val context: Context) {
    private val _callLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val callLogs: StateFlow<List<CallLogItem>> = _callLogs.asStateFlow()

    private val _spamCallLogs = MutableStateFlow<List<CallLogItem>>(emptyList())
    val spamCallLogs: StateFlow<List<CallLogItem>> = _spamCallLogs.asStateFlow()

    private val _hasUnreadSpamCalls = MutableStateFlow(false)
    val hasUnreadSpamCalls: StateFlow<Boolean> = _hasUnreadSpamCalls.asStateFlow()

    private val _unreadMissedCallCount = MutableStateFlow(0)
    val unreadMissedCallCount: StateFlow<Int> = _unreadMissedCallCount.asStateFlow()

    private val _blockedNumbers = MutableStateFlow<Set<String>>(emptySet())
    val blockedNumbers: StateFlow<Set<String>> = _blockedNumbers.asStateFlow()

    private val prefs = context.getSharedPreferences("mphone_blocked_calls_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val DELETED_LOGS_FILE = "mphone_deleted_call_logs.json"
    }

    init {
        _hasUnreadSpamCalls.value = prefs.getBoolean("has_unread_spam_calls", false)
        _unreadMissedCallCount.value = prefs.getInt("unread_missed_calls_count", 0)
        _blockedNumbers.value = getBlockedNumbers()
    }

    private fun isSamePhoneNumber(num1: String, num2: String): Boolean {
        val c1 = ContactMerger.normalizePhoneNumber(num1)
        val c2 = ContactMerger.normalizePhoneNumber(num2)
        if (c1.isBlank() || c2.isBlank()) return false
        if (c1 == c2) return true
        if (c1.length >= 7 && c2.length >= 7 && c1.takeLast(7) == c2.takeLast(7)) return true
        return false
    }

    private fun getDeletedCallLogKeysFromFile(): Set<String> {
        try {
            val file = File(context.filesDir, DELETED_LOGS_FILE)
            if (!file.exists()) return emptySet()
            val text = file.readText()
            if (text.isBlank()) return emptySet()
            val jsonArray = JSONArray(text)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i))
            }
            return set
        } catch (e: Exception) {
            e.printStackTrace()
            return emptySet()
        }
    }

    private fun saveDeletedCallLogKeysToFile(set: Set<String>) {
        try {
            val jsonArray = JSONArray()
            set.forEach { jsonArray.put(it) }
            val file = File(context.filesDir, DELETED_LOGS_FILE)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDeletedCallLogKeys(): Set<String> {
        val prefsSet = prefs.getStringSet("deleted_call_logs", null) ?: emptySet()
        val fileSet = getDeletedCallLogKeysFromFile()
        return (prefsSet + fileSet).toSet()
    }

    private fun markSingleLogDeleted(logId: String) {
        if (logId.isBlank()) return
        val current = getDeletedCallLogKeys().toMutableSet()
        current.add("id_$logId")
        current.add(logId)
        prefs.edit().putStringSet("deleted_call_logs", current).apply()
        saveDeletedCallLogKeysToFile(current)
    }

    private fun markNumberLogsDeleted(cleanNumber: String, contactId: String? = null) {
        val current = getDeletedCallLogKeys().toMutableSet()
        if (cleanNumber.isNotBlank()) {
            current.add(cleanNumber)
            if (cleanNumber.length >= 7) {
                current.add(cleanNumber.takeLast(7))
            }
        }
        if (!contactId.isNullOrBlank()) current.add(contactId)
        prefs.edit().putStringSet("deleted_call_logs", current).apply()
        saveDeletedCallLogKeysToFile(current)
    }

    fun markSpamCallsAsRead() {
        _hasUnreadSpamCalls.value = false
        prefs.edit().putBoolean("has_unread_spam_calls", false).apply()
    }

    fun markMissedCallsAsRead() {
        _unreadMissedCallCount.value = 0
        prefs.edit().putInt("unread_missed_calls_count", 0).apply()
    }

    private fun markNewSpamCallArrived() {
        _hasUnreadSpamCalls.value = true
        prefs.edit().putBoolean("has_unread_spam_calls", true).apply()
    }

    private fun updateUnreadMissedCount(missedCountFromLog: Int) {
        val savedCount = prefs.getInt("unread_missed_calls_count", -1)
        if (savedCount == -1) {
            _unreadMissedCallCount.value = missedCountFromLog
            prefs.edit().putInt("unread_missed_calls_count", missedCountFromLog).apply()
        } else {
            _unreadMissedCallCount.value = savedCount
        }
    }

    fun getBlockedNumbers(): Set<String> {
        return prefs.getStringSet("blocked_numbers", emptySet()) ?: emptySet()
    }

    fun getUnblockedNumbers(): Set<String> {
        return prefs.getStringSet("unblocked_numbers", emptySet()) ?: emptySet()
    }

    fun isNumberBlocked(number: String): Boolean {
        val clean = ContactMerger.normalizePhoneNumber(number)
        if (clean.isBlank()) return false
        val unblockedSet = getUnblockedNumbers()
        if (unblockedSet.contains(clean)) return false

        return getBlockedNumbers().contains(clean) ||
                CountryCodeFilter.isFromOutsideTurkey(number) ||
                TurkeySpamNumberBlocker.isTurkishSpamNumber(number)
    }

    fun loadCallLogs(contacts: List<Contact> = emptyList()) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

        val deletedCallLogKeys = getDeletedCallLogKeys()

        if (!hasPermission) {
            _callLogs.value = MockData.sampleCallLogs.filter { log ->
                val cleanNum = ContactMerger.normalizePhoneNumber(log.phoneNumber)
                !deletedCallLogKeys.contains("id_${log.id}") &&
                        !deletedCallLogKeys.contains(log.id) &&
                        !deletedCallLogKeys.contains(cleanNum)
            }
            return
        }

        try {
            val list = mutableListOf<CallLogItem>()
            val spamList = mutableListOf<CallLogItem>()
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            val dateFormat = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
            val blockedSet = getBlockedNumbers()

            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) else ""
                    val cachedName = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "Unknown" else "Unknown"
                    val typeInt = if (typeIdx >= 0) it.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                    val dateMillis = if (dateIdx >= 0) it.getLong(dateIdx) else 0L
                    val durationSec = if (durIdx >= 0) it.getInt(durIdx) else 0

                    val matchedContact = ContactMerger.findContactForNumber(contacts, number)
                    val cleanNum = ContactMerger.normalizePhoneNumber(number)

                    val isDeleted = deletedCallLogKeys.contains("id_$id") ||
                            deletedCallLogKeys.contains(id) ||
                            (cleanNum.isNotBlank() && deletedCallLogKeys.contains(cleanNum)) ||
                            (matchedContact?.id != null && deletedCallLogKeys.contains(matchedContact.id)) ||
                            (cleanNum.length >= 7 && deletedCallLogKeys.any { key -> key.length >= 7 && cleanNum.takeLast(7) == key.takeLast(7) && !key.startsWith("id_") })

                    if (isDeleted) {
                        continue
                    }

                    val corporateName = CorporateCallerIdRegistry.getCorporateName(number)
                    val displayName = matchedContact?.name ?: corporateName ?: number

                    val unblockedSet = getUnblockedNumbers()
                    val isUserUnblocked = unblockedSet.contains(cleanNum)
                    val isUserBlocked = blockedSet.contains(cleanNum)
                    val isOutsideTurkey = CountryCodeFilter.isFromOutsideTurkey(number)
                    val isTurkishSpam = TurkeySpamNumberBlocker.isTurkishSpamNumber(number)
                    val isBlockedCall = !isUserUnblocked && (isUserBlocked || isOutsideTurkey || isTurkishSpam)

                    val callType = if (isBlockedCall && typeInt == CallLog.Calls.INCOMING_TYPE) {
                        CallType.REJECTED
                    } else {
                        when (typeInt) {
                            CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                            CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                            CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                            else -> CallType.INCOMING
                        }
                    }

                    val dateStr = if (dateMillis > 0) dateFormat.format(Date(dateMillis)) else "Recent"
                    val durationStr = when {
                        isUserBlocked -> "0s (User Blocked)"
                        isOutsideTurkey -> "0s (Blocked International)"
                        isTurkishSpam -> "0s (Blocked Spam - TR)"
                        durationSec > 0 -> "${durationSec / 60}m ${durationSec % 60}s"
                        else -> "0s"
                    }

                    val item = CallLogItem(
                        id = id.ifBlank { System.currentTimeMillis().toString() },
                        name = displayName,
                        phoneNumber = number,
                        type = callType,
                        timestamp = dateStr,
                        duration = durationStr,
                        contactId = matchedContact?.id,
                        photoUri = matchedContact?.photoUri
                    )

                    if (isBlockedCall) {
                        spamList.add(item)
                    } else {
                        list.add(item)
                    }
                }
            }

            _callLogs.value = list
            _spamCallLogs.value = spamList

            val missedCount = _callLogs.value.count { it.type == CallType.MISSED || it.type == CallType.REJECTED }
            updateUnreadMissedCount(missedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            val deletedCallLogKeys = getDeletedCallLogKeys()
            _callLogs.value = _callLogs.value.filter { log ->
                val cleanNum = ContactMerger.normalizePhoneNumber(log.phoneNumber)
                !deletedCallLogKeys.contains("id_${log.id}") &&
                        !deletedCallLogKeys.contains(log.id) &&
                        !deletedCallLogKeys.contains(cleanNum)
            }
        }
    }

    fun addCallLog(name: String, number: String, type: CallType, duration: String) {
        val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeStr = "Today, ${dateFormat.format(Date())}"
        val newItem = CallLogItem(
            id = System.currentTimeMillis().toString(),
            name = name.ifBlank { number },
            phoneNumber = number,
            type = type,
            timestamp = timeStr,
            duration = duration
        )
        val clean = ContactMerger.normalizePhoneNumber(number)
        if (getBlockedNumbers().contains(clean) || CountryCodeFilter.isFromOutsideTurkey(number) || TurkeySpamNumberBlocker.isTurkishSpamNumber(number)) {
            _spamCallLogs.value = listOf(newItem.copy(type = CallType.REJECTED, duration = "0s (Blocked)")) + _spamCallLogs.value
            markNewSpamCallArrived()
        } else {
            _callLogs.value = listOf(newItem) + _callLogs.value
            if (type == CallType.MISSED || type == CallType.REJECTED) {
                val newCount = _unreadMissedCallCount.value + 1
                _unreadMissedCallCount.value = newCount
                prefs.edit().putInt("unread_missed_calls_count", newCount).apply()
            }
        }
    }

    fun blockNumber(number: String) {
        val cleanNumber = ContactMerger.normalizePhoneNumber(number)
        if (cleanNumber.isNotBlank()) {
            val blocked = getBlockedNumbers().toMutableSet()
            blocked.add(cleanNumber)
            prefs.edit().putStringSet("blocked_numbers", blocked).apply()
            _blockedNumbers.value = blocked

            val unblocked = getUnblockedNumbers().toMutableSet()
            if (unblocked.remove(cleanNumber)) {
                prefs.edit().putStringSet("unblocked_numbers", unblocked).apply()
            }

            val logsToBlock = _callLogs.value.filter {
                isSamePhoneNumber(it.phoneNumber, number) || ContactMerger.normalizePhoneNumber(it.phoneNumber) == cleanNumber
            }
            _callLogs.value = _callLogs.value.filter {
                !isSamePhoneNumber(it.phoneNumber, number) && ContactMerger.normalizePhoneNumber(it.phoneNumber) != cleanNumber
            }
            _spamCallLogs.value = (logsToBlock.map { it.copy(type = CallType.REJECTED, duration = "0s (User Blocked)") } + _spamCallLogs.value).distinctBy { it.id }
        }
    }

    fun unblockNumber(number: String) {
        val cleanNumber = ContactMerger.normalizePhoneNumber(number)
        if (cleanNumber.isNotBlank()) {
            val blocked = getBlockedNumbers().toMutableSet()
            blocked.remove(cleanNumber)
            prefs.edit().putStringSet("blocked_numbers", blocked).apply()
            _blockedNumbers.value = blocked

            val unblocked = getUnblockedNumbers().toMutableSet()
            unblocked.add(cleanNumber)
            prefs.edit().putStringSet("unblocked_numbers", unblocked).apply()

            val logsToRestore = _spamCallLogs.value.filter {
                isSamePhoneNumber(it.phoneNumber, number) || ContactMerger.normalizePhoneNumber(it.phoneNumber) == cleanNumber
            }
            _spamCallLogs.value = _spamCallLogs.value.filter {
                !isSamePhoneNumber(it.phoneNumber, number) && ContactMerger.normalizePhoneNumber(it.phoneNumber) != cleanNumber
            }
            _callLogs.value = (logsToRestore.map { it.copy(type = CallType.INCOMING, duration = "0s") } + _callLogs.value).distinctBy { it.id }
        }
    }

    fun deleteCallLog(id: String) {
        markSingleLogDeleted(id)

        _callLogs.value = _callLogs.value.filter { it.id != id }
        _spamCallLogs.value = _spamCallLogs.value.filter { it.id != id }

        try {
            if (id.isNotBlank() && id.all { it.isDigit() }) {
                context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} = ?",
                    arrayOf(id)
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun deleteCallLogsForNumber(number: String, contactId: String? = null) {
        val cleanNumber = ContactMerger.normalizePhoneNumber(number)
        markNumberLogsDeleted(cleanNumber, contactId)

        val updatedCallLogs = _callLogs.value.filter { log ->
            val logClean = ContactMerger.normalizePhoneNumber(log.phoneNumber)
            val isMatch = isSamePhoneNumber(log.phoneNumber, number) ||
                    log.phoneNumber == number ||
                    (cleanNumber.isNotBlank() && logClean == cleanNumber) ||
                    (contactId != null && log.contactId == contactId) ||
                    (cleanNumber.length >= 7 && logClean.length >= 7 && logClean.takeLast(7) == cleanNumber.takeLast(7))
            !isMatch
        }
        _callLogs.value = updatedCallLogs

        val updatedSpamLogs = _spamCallLogs.value.filter { log ->
            val logClean = ContactMerger.normalizePhoneNumber(log.phoneNumber)
            val isMatch = isSamePhoneNumber(log.phoneNumber, number) ||
                    log.phoneNumber == number ||
                    (cleanNumber.isNotBlank() && logClean == cleanNumber) ||
                    (contactId != null && log.contactId == contactId) ||
                    (cleanNumber.length >= 7 && logClean.length >= 7 && logClean.takeLast(7) == cleanNumber.takeLast(7))
            !isMatch
        }
        _spamCallLogs.value = updatedSpamLogs

        try {
            if (cleanNumber.isNotBlank()) {
                val queryTerm = if (cleanNumber.length >= 7) cleanNumber.takeLast(7) else cleanNumber
                context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls.NUMBER} LIKE ?",
                    arrayOf("%$queryTerm%")
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
