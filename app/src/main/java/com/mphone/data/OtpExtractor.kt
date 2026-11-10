package com.mphone.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object OtpExtractor {

    private val OTP_KEYWORDS = listOf(
        "otp", "code", "kod", "kodu", "şifre", "sifre",
        "doğrulama", "dogrulama", "verification", "security",
        "passcode", "pin", "auth", "login"
    )

    private val DIGIT_REGEX = Regex("(?<!\\d)\\d{4,8}(?!\\d)")

    fun extractOtp(body: String): String? {
        val lowerBody = body.lowercase()
        val containsKeyword = OTP_KEYWORDS.any { lowerBody.contains(it) }

        if (containsKeyword) {
            val match = DIGIT_REGEX.find(body)
            if (match != null) {
                return match.value
            }
        }
        return null
    }

    fun copyOtpToClipboardIfPresent(context: Context, body: String): String? {
        val otpCode = extractOtp(body)
        if (!otpCode.isNullOrBlank()) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null) {
                    val clip = ClipData.newPlainText("OTP Code", otpCode)
                    clipboard.setPrimaryClip(clip)

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "OTP Code $otpCode copied to clipboard", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return otpCode
        }
        return null
    }
}
