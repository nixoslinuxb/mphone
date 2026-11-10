package com.mphone.service

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.mphone.data.CountryCodeFilter
import com.mphone.data.OtpExtractor
import com.mphone.data.SmsEventBus
import com.mphone.data.SmsLocalCache
import com.mphone.data.TurkeySpamNumberBlocker

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (!messages.isNullOrEmpty()) {
                val sender = messages[0].originatingAddress ?: "Unknown"
                val bodyBuilder = StringBuilder()
                for (msg in messages) {
                    bodyBuilder.append(msg.messageBody)
                }
                val body = bodyBuilder.toString()

                // Check international & Turkish spam filters
                val isForeign = CountryCodeFilter.isFromOutsideTurkey(sender)
                val isSpam = TurkeySpamNumberBlocker.isSpamSms(sender, body)

                if (!isForeign && !isSpam && body.isNotBlank()) {
                    // Auto-extract and copy OTP verification code to clipboard
                    OtpExtractor.copyOtpToClipboardIfPresent(context, body)

                    // Save to local disk cache immediately
                    SmsLocalCache.appendIncomingSms(context, sender, body)

                    // Write to System Telephony Provider Inbox
                    try {
                        val values = ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, sender)
                            put(Telephony.Sms.BODY, body)
                            put(Telephony.Sms.DATE, System.currentTimeMillis())
                            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                            put(Telephony.Sms.READ, 0)
                        }
                        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Emit in real-time to active app UI & repositories
                    SmsEventBus.emitIncomingSms(sender, body)

                    // Show floating Dynamic Island Heads-Up system notification
                    DynamicIslandNotificationHelper.showBackgroundSmsNotification(context, sender, body)
                }
            }
        }
    }
}
