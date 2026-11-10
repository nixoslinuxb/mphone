package com.mphone.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.mphone.service.MphoneInCallService

object TelecomCallManager {

    private var toneGenerator: ToneGenerator? = null

    fun acceptIncomingCall(context: Context) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return

        val hasAnswerPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED

        try {
            if (hasAnswerPermission) {
                @Suppress("DEPRECATION")
                telecomManager.acceptRingingCall()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun endOrDeclineCall(context: Context) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return

        val hasAnswerPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        try {
            if (hasAnswerPermission) {
                @Suppress("DEPRECATION")
                telecomManager.endCall()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleSpeakerphone(context: Context, enable: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.mode = AudioManager.MODE_IN_CALL

            // 1. InCallService Route
            MphoneInCallService.activeInstance?.setSpeakerRoute(enable)

            // 2. Communication Device Route (Android 12+)
            if (enable) {
                val speakerDevice = audioManager.availableCommunicationDevices.find {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerDevice != null) {
                    audioManager.setCommunicationDevice(speakerDevice)
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
            } else {
                audioManager.clearCommunicationDevice()
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun toggleMicrophoneMute(context: Context, enable: Boolean) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioManager.isMicrophoneMute = enable
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun sendDtmfTone(digit: Char) {
        try {
            MphoneInCallService.activeInstance?.playDtmfDigit(digit)

            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
            }

            val tone = when (digit) {
                '0' -> ToneGenerator.TONE_DTMF_0
                '1' -> ToneGenerator.TONE_DTMF_1
                '2' -> ToneGenerator.TONE_DTMF_2
                '3' -> ToneGenerator.TONE_DTMF_3
                '4' -> ToneGenerator.TONE_DTMF_4
                '5' -> ToneGenerator.TONE_DTMF_5
                '6' -> ToneGenerator.TONE_DTMF_6
                '7' -> ToneGenerator.TONE_DTMF_7
                '8' -> ToneGenerator.TONE_DTMF_8
                '9' -> ToneGenerator.TONE_DTMF_9
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> -1
            }

            if (tone != -1) {
                toneGenerator?.startTone(tone, 150)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
