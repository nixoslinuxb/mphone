package com.mphone.service

import android.app.NotificationManager
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.mphone.data.SmsRepository
import com.mphone.data.SmsSenderId

class SmsReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sms_sender") ?: return
        val notifId = intent.getIntExtra("notification_id", 0)

        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence("key_text_reply")?.toString()

        if (!replyText.isNullOrBlank()) {
            if (SmsSenderId.isBrandSenderId(sender)) {
                Toast.makeText(context, "Marka mesajlarına yanıt gönderilemez.", Toast.LENGTH_LONG).show()
                return
            }
            val smsRepo = SmsRepository(context.applicationContext)
            smsRepo.sendMessage(sender, replyText)

            Toast.makeText(context, "Reply sent", Toast.LENGTH_SHORT).show()

            // Automatically dismiss and close system SMS notification
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notifId > 0) {
                    notificationManager.cancel(notifId)
                }
                notificationManager.cancelAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
