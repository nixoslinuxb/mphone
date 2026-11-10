package com.mphone.model

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED
}

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null,
    val avatarColorIndex: Int = 0,
    val note: String = ""
)

data class CallLogItem(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val type: CallType,
    val timestamp: String,
    val duration: String,
    val contactId: String? = null,
    val photoUri: String? = null
)

data class SmsMessage(
    val id: String,
    val threadId: String,
    val senderName: String,
    val senderNumber: String,
    val body: String,
    val timestamp: String,
    val isFromUser: Boolean
)

data class SmsThread(
    val id: String,
    val contactName: String,
    val contactNumber: String,
    val lastMessage: String,
    val timestamp: String,
    val lastTimestampMillis: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val avatarColorIndex: Int = 0,
    val photoUri: String? = null,
    val messages: List<SmsMessage> = emptyList()
)
