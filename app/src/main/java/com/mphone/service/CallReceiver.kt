package com.mphone.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.mphone.MainActivity
import com.mphone.data.CallEventBus
import com.mphone.data.CallLogRepository
import com.mphone.data.TelecomCallManager

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                // Call ended on network level by either party
                CallEventBus.emitCallEnded()
            } else if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                @Suppress("DEPRECATION")
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""

                val callLogRepo = CallLogRepository(context)
                val isBlocked = callLogRepo.isNumberBlocked(incomingNumber)

                if (isBlocked) {
                    // Instantly reject call on network level so phone does not ring
                    TelecomCallManager.endOrDeclineCall(context)
                } else {
                    try {
                        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                            )
                            putExtra("incoming_call_number", incomingNumber)
                        }
                        context.startActivity(fullScreenIntent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
