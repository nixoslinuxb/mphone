package com.mphone.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mphone.data.SmsRepository

class SmsMarkReadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sms_sender") ?: return
        val notifId = intent.getIntExtra("notification_id", 0)

        val smsRepo = SmsRepository(context.applicationContext)
        smsRepo.markThreadAsRead(sender)

        if (notifId > 0) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notifId)
        }
    }
}
