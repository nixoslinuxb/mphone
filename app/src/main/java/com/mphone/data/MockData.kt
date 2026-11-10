package com.mphone.data

import com.mphone.model.CallLogItem
import com.mphone.model.CallType
import com.mphone.model.Contact
import com.mphone.model.SmsMessage
import com.mphone.model.SmsThread

object MockData {
    val sampleContacts = listOf(
        Contact("3", "Mehmet Demir", "+90 (535) 234-5678", photoUri = null, avatarColorIndex = 2, note = "Tasarım Lideri"),
        Contact("5", "Can Öztürk", "+90 (537) 456-7890", photoUri = null, avatarColorIndex = 4, note = "Güvenlik Uzmanı"),
        Contact("6", "Burcu Arslan", "+90 (538) 567-8901", photoUri = null, avatarColorIndex = 5, note = "Spor Antrenörü"),
        Contact("7", "Aydın Şahin", "+90 (539) 678-9012", photoUri = null, avatarColorIndex = 0, note = "Mali Müşavir"),
        Contact("8", "Selin Koç", "+90 (542) 789-0123", photoUri = null, avatarColorIndex = 1, note = "Kız Kardeşim"),
        Contact("9", "John Foreigner (USA)", "+1 (555) 890-1234", photoUri = null, avatarColorIndex = 2, note = "Foreign Contact (Outside TR)")
    )

    val sampleCallLogs = listOf(
        CallLogItem("c7", "Şüpheli Robot Arama (0850)", "+90 (850) 484-0000", CallType.REJECTED, "Bugün, 08:00", "0sn (Blocked Spam - TR)", null),
        CallLogItem("c4", "+1 (800) 555-9999", "+1 (800) 555-9999", CallType.REJECTED, "Dün, 15:10", "0sn (Blocked Non-TR)", null),
        CallLogItem("c5", "Selin Koç", "+90 (542) 789-0123", CallType.INCOMING, "24 Ekim, 20:00", "28dk 05sn", "8"),
        CallLogItem("c6", "Mehmet Demir", "+90 (535) 234-5678", CallType.OUTGOING, "23 Ekim, 11:20", "2dk 10sn", "3")
    )

    val sampleSmsThreads = listOf(
        SmsThread(
            id = "t_selin",
            contactName = "Selin Koç",
            contactNumber = "+90 (542) 789-0123",
            lastMessage = "Akşam yemeğe geliyor musun?",
            timestamp = "12:30",
            unreadCount = 1,
            avatarColorIndex = 1,
            messages = listOf(
                SmsMessage("m_s1", "t_selin", "Selin Koç", "+90 (542) 789-0123", "Akşam yemeğe geliyor musun?", "12:30", false)
            )
        )
    )
}
