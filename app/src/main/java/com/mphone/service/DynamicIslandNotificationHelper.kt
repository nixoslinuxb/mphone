package com.mphone.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.mphone.MainActivity
import com.mphone.R
import com.mphone.data.ContactMerger
import com.mphone.data.SmsSenderId

object DynamicIslandNotificationHelper {

    private const val SMS_CHANNEL_ID = "mphone_sms_channel"
    private const val SMS_CHANNEL_NAME = "mPhone SMS Notifications"

    private const val CALL_CHANNEL_ID = "mphone_call_channel"
    private const val CALL_CHANNEL_NAME = "mPhone Call Notifications"

    @Volatile
    var activeChatNumber: String? = null

    private var lastNotifSender: String = ""
    private var lastNotifSnippet: String = ""
    private var lastNotifTime: Long = 0L

    @Synchronized
    fun showBackgroundSmsNotification(context: Context, sender: String, messageSnippet: String) {
        // Suppress notification if user is currently on ChatScreen with sender!
        val currentActive = activeChatNumber
        if (!currentActive.isNullOrBlank()) {
            val cleanActive = ContactMerger.normalizePhoneNumber(currentActive)
            val cleanSender = ContactMerger.normalizePhoneNumber(sender)
            if (cleanActive.isNotBlank() && cleanSender.isNotBlank() && cleanActive == cleanSender) {
                return
            }
        }

        val now = System.currentTimeMillis()
        if (lastNotifSender == sender && lastNotifSnippet == messageSnippet && (now - lastNotifTime) < 5000L) {
            return // Skip duplicate notification from dual receivers!
        }
        lastNotifSender = sender
        lastNotifSnippet = messageSnippet
        lastNotifTime = now

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            SMS_CHANNEL_ID,
            SMS_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Floating Dynamic Island SMS alerts when app is in background"
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)

        val notificationId = (System.currentTimeMillis() % 10000).toInt() + 2000
        val displayName = resolveContactDisplayName(context, sender)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("sms_sender", sender)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 1. Native Quick Reply Action with RemoteInput
        val remoteInput = RemoteInput.Builder("key_text_reply")
            .setLabel("Quick Reply...")
            .build()

        val replyIntent = Intent(context, SmsReplyReceiver::class.java).apply {
            action = "com.mphone.ACTION_QUICK_REPLY"
            putExtra("sms_sender", sender)
            putExtra("notification_id", notificationId)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Quick Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // 2. Native Mark as Read Action
        val markReadIntent = Intent(context, SmsMarkReadReceiver::class.java).apply {
            action = "com.mphone.ACTION_MARK_READ"
            putExtra("sms_sender", sender)
            putExtra("notification_id", notificationId)
        }

        val markReadPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 10000,
            markReadIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val markReadAction = NotificationCompat.Action.Builder(
            R.mipmap.ic_launcher,
            "Mark as Read",
            markReadPendingIntent
        ).build()

        val titleText = if (displayName != sender && displayName.isNotBlank() && displayName != "Incoming Call") "$displayName ($sender)" else sender
        val compactView = RemoteViews(context.packageName, R.layout.notification_dynamic_island_compact).apply {
            setTextViewText(R.id.notif_title, titleText)
            setTextViewText(R.id.notif_text, messageSnippet)
        }

        val notification = NotificationCompat.Builder(context, SMS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setCustomContentView(compactView)
            .setCustomHeadsUpContentView(compactView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .apply {
                // Brand / alphanumeric senders cannot receive replies.
                if (!SmsSenderId.isBrandSenderId(sender)) {
                    addAction(replyAction)
                }
            }
            .addAction(markReadAction)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    fun showBackgroundIncomingCall(context: Context, number: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CALL_CHANNEL_ID,
            CALL_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Full screen incoming call alerts when app is in background"
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)

        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("incoming_call_number", number)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val displayName = resolveContactDisplayName(context, number)

        val compactView = RemoteViews(context.packageName, R.layout.notification_dynamic_island_compact).apply {
            setTextViewText(R.id.notif_title, displayName)
            setTextViewText(R.id.notif_text, "Swipe to Answer or Decline")
        }

        val notification = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setCustomContentView(compactView)
            .setCustomHeadsUpContentView(compactView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }

    private fun resolveContactDisplayName(context: Context, number: String): String {
        if (number.isBlank() || number == "Unknown") return "Incoming Call"
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
}
