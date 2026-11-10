package com.mphone.service

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.mphone.MainActivity
import com.mphone.data.CallEventBus
import com.mphone.data.CallLogRepository

class MphoneInCallService : InCallService() {

    companion object {
        var activeInstance: MphoneInCallService? = null
            private set
        var activeCall: Call? = null
            private set
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        activeInstance = this
        activeCall = call

        @Suppress("DEPRECATION")
        if (call.state == Call.STATE_ACTIVE) {
            CallEventBus.emitCallConnected()
        }

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call?, state: Int) {
                super.onStateChanged(c, state)
                if (state == Call.STATE_ACTIVE) {
                    CallEventBus.emitCallConnected()
                } else if (state == Call.STATE_DISCONNECTED) {
                    CallEventBus.emitCallEnded()
                }
            }
        })

        val handle = call.details.handle?.schemeSpecificPart ?: ""
        val callLogRepo = CallLogRepository(applicationContext)
        val isBlocked = callLogRepo.isNumberBlocked(handle)

        val isIncomingCall = call.details.callDirection == Call.Details.DIRECTION_INCOMING

        if (isIncomingCall && isBlocked) {
            try {
                @Suppress("DEPRECATION")
                call.reject(false, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        // Direct full screen launch for incoming calls (No notification banner)
        if (isIncomingCall) {
            try {
                val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    putExtra("incoming_call_number", handle)
                }
                startActivity(fullScreenIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (activeCall == call) activeCall = null
        activeInstance = null
        CallEventBus.emitCallEnded()
    }

    fun setSpeakerRoute(enable: Boolean) {
        try {
            val route = if (enable) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
            setAudioRoute(route)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playDtmfDigit(digit: Char) {
        try {
            activeCall?.playDtmfTone(digit)
            activeCall?.stopDtmfTone()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
