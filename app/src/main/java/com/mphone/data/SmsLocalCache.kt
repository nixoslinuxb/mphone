package com.mphone.data

import android.content.Context
import com.mphone.model.SmsMessage
import com.mphone.model.SmsThread
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SmsLocalCache {

    private const val FILE_NAME = "mphone_sms_cache.json"

    fun saveThreads(context: Context, threads: List<SmsThread>) {
        try {
            val jsonArray = JSONArray()
            threads.forEach { thread ->
                val threadObj = JSONObject().apply {
                    put("id", thread.id)
                    put("contactName", thread.contactName)
                    put("contactNumber", thread.contactNumber)
                    put("lastMessage", thread.lastMessage)
                    put("timestamp", thread.timestamp)
                    put("lastTimestampMillis", thread.lastTimestampMillis)
                    put("unreadCount", thread.unreadCount)
                    put("avatarColorIndex", thread.avatarColorIndex)
                    put("photoUri", thread.photoUri ?: "")

                    val msgsArray = JSONArray()
                    thread.messages.forEach { msg ->
                        val msgObj = JSONObject().apply {
                            put("id", msg.id)
                            put("threadId", msg.threadId)
                            put("senderName", msg.senderName)
                            put("senderNumber", msg.senderNumber)
                            put("body", msg.body)
                            put("timestamp", msg.timestamp)
                            put("isFromUser", msg.isFromUser)
                        }
                        msgsArray.put(msgObj)
                    }
                    put("messages", msgsArray)
                }
                jsonArray.put(threadObj)
            }

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadThreads(context: Context): List<SmsThread> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        try {
            val jsonStr = file.readText()
            if (jsonStr.isBlank()) return emptyList()

            val list = mutableListOf<SmsThread>()
            val jsonArray = JSONArray(jsonStr)

            for (i in 0 until jsonArray.length()) {
                val threadObj = jsonArray.getJSONObject(i)
                val id = threadObj.optString("id", "")
                val contactName = threadObj.optString("contactName", "")
                val contactNumber = threadObj.optString("contactNumber", "")
                val lastMessage = threadObj.optString("lastMessage", "")
                val timestamp = threadObj.optString("timestamp", "Recent")
                val lastTimestampMillis = threadObj.optLong("lastTimestampMillis", System.currentTimeMillis())
                val unreadCount = threadObj.optInt("unreadCount", 0)
                val avatarColorIndex = threadObj.optInt("avatarColorIndex", 0)
                val photoUri = threadObj.optString("photoUri", "").ifBlank { null }

                val msgsArray = threadObj.optJSONArray("messages") ?: JSONArray()
                val msgsList = mutableListOf<SmsMessage>()

                for (j in 0 until msgsArray.length()) {
                    val msgObj = msgsArray.getJSONObject(j)
                    msgsList.add(
                        SmsMessage(
                            id = msgObj.optString("id", ""),
                            threadId = msgObj.optString("threadId", id),
                            senderName = msgObj.optString("senderName", ""),
                            senderNumber = msgObj.optString("senderNumber", ""),
                            body = msgObj.optString("body", ""),
                            timestamp = msgObj.optString("timestamp", ""),
                            isFromUser = msgObj.optBoolean("isFromUser", false)
                        )
                    )
                }

                if (contactNumber.isNotBlank()) {
                    list.add(
                        SmsThread(
                            id = id,
                            contactName = contactName,
                            contactNumber = contactNumber,
                            lastMessage = lastMessage,
                            timestamp = timestamp,
                            lastTimestampMillis = lastTimestampMillis,
                            unreadCount = unreadCount,
                            avatarColorIndex = avatarColorIndex,
                            photoUri = photoUri,
                            messages = msgsList
                        )
                    )
                }
            }
            return list.sortedByDescending { it.lastTimestampMillis }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    fun appendIncomingSms(context: Context, sender: String, body: String) {
        if (sender.isBlank() || body.isBlank()) return
        if (CountryCodeFilter.isFromOutsideTurkey(sender) || TurkeySpamNumberBlocker.isSpamSms(sender, body)) return

        try {
            val currentThreads = loadThreads(context).toMutableList()
            val cleanSender = ContactMerger.normalizePhoneNumber(sender).ifBlank { sender }
            val nowMillis = System.currentTimeMillis()
            val dateFormat = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
            val nowStr = dateFormat.format(Date(nowMillis))

            val newMsg = SmsMessage(
                id = nowMillis.toString(),
                threadId = cleanSender,
                senderName = sender,
                senderNumber = sender,
                body = body,
                timestamp = nowStr,
                isFromUser = false
            )

            val existingIdx = currentThreads.indexOfFirst {
                val cleanContact = ContactMerger.normalizePhoneNumber(it.contactNumber).ifBlank { it.contactNumber }
                cleanContact == cleanSender ||
                        (cleanSender.length >= 7 && cleanContact.length >= 7 && cleanContact.takeLast(7) == cleanSender.takeLast(7))
            }

            if (existingIdx >= 0) {
                val existing = currentThreads[existingIdx]
                val isDuplicate = existing.messages.any {
                    it.body.trim() == body.trim() && !it.isFromUser && (nowMillis - (it.id.toLongOrNull() ?: 0L)) < 5000L
                }
                if (!isDuplicate) {
                    val combined = (existing.messages + newMsg)
                    currentThreads[existingIdx] = existing.copy(
                        lastMessage = body,
                        timestamp = nowStr,
                        lastTimestampMillis = nowMillis,
                        unreadCount = existing.unreadCount + 1,
                        messages = combined
                    )
                }
            } else {
                val newThread = SmsThread(
                    id = "t_$nowMillis",
                    contactName = sender,
                    contactNumber = sender,
                    lastMessage = body,
                    timestamp = nowStr,
                    lastTimestampMillis = nowMillis,
                    unreadCount = 1,
                    avatarColorIndex = (0..5).random(),
                    messages = listOf(newMsg)
                )
                currentThreads.add(0, newThread)
            }
            saveThreads(context, currentThreads)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
