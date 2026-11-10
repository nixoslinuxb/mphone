package com.mphone.data

import java.util.concurrent.ConcurrentHashMap

sealed class SendStatus {
    data object Success : SendStatus()
    data class RateLimited(val message: String, val secondsToWait: Int) : SendStatus()
    data class DuplicateBlocked(val message: String) : SendStatus()
    data class BrandBlocked(val message: String) : SendStatus()
}

class SmsRateLimiter(
    private val minIntervalMs: Long = 1500L,          // 1.5s delay between sends
    private val maxWindowMessages: Int = 5,           // Max 5 msgs per 15s window
    private val windowMs: Long = 15000L,              // 15 second window
    private val duplicateWindowMs: Long = 30000L      // 30s duplicate check
) {
    private var lastGlobalSendTime: Long = 0L
    private val sendTimestamps = mutableListOf<Long>()
    private val recentMessagesMap = ConcurrentHashMap<String, StringAndTimestamp>()

    private data class StringAndTimestamp(val text: String, val timestamp: Long)

    @Synchronized
    fun checkCanSend(recipientKey: String, body: String): SendStatus {
        val now = System.currentTimeMillis()

        // 0. Automatically block SMS coming from / going to outside of Türkiye (+90)
        if (CountryCodeFilter.isFromOutsideTurkey(recipientKey)) {
            val reason = CountryCodeFilter.getBlockReason(recipientKey)
                ?: "SMS blocked: Destination number is outside of Türkiye."
            return SendStatus.RateLimited(message = reason, secondsToWait = 0)
        }

        // 0.1 Check Türkiye Spam & Robocall Database / Spam Keywords
        if (TurkeySpamNumberBlocker.isSpamSms(recipientKey, body)) {
            val reason = TurkeySpamNumberBlocker.getSpamBlockReason(recipientKey, body)
                ?: "SMS blocked: Flagged in Türkiye Spam & Robocall Database."
            return SendStatus.RateLimited(message = reason, secondsToWait = 0)
        }

        // 1. Check minimum interval between consecutive sends
        val timeSinceLast = now - lastGlobalSendTime
        if (lastGlobalSendTime > 0 && timeSinceLast < minIntervalMs) {
            val waitSec = ((minIntervalMs - timeSinceLast) / 1000 + 1).toInt()
            return SendStatus.RateLimited(
                message = "Slow down! Please wait $waitSec sec before sending another SMS.",
                secondsToWait = waitSec
            )
        }

        // 2. Clean sliding window
        sendTimestamps.removeAll { now - it > windowMs }

        // 3. Check window rate limit (SMS Bombing prevention)
        if (sendTimestamps.size >= maxWindowMessages) {
            val oldestInWindow = sendTimestamps.firstOrNull() ?: now
            val retryInMs = windowMs - (now - oldestInWindow)
            val waitSec = (retryInMs / 1000 + 1).coerceAtLeast(1).toInt()
            return SendStatus.RateLimited(
                message = "SMS Bombing protection active. Rate limit reached ($maxWindowMessages msgs / 15s). Try again in $waitSec sec.",
                secondsToWait = waitSec
            )
        }

        // 4. Duplicate message protection
        val lastDuplicate = recentMessagesMap[recipientKey]
        if (lastDuplicate != null &&
            lastDuplicate.text.trim().equals(body.trim(), ignoreCase = true) &&
            (now - lastDuplicate.timestamp < duplicateWindowMs)
        ) {
            return SendStatus.DuplicateBlocked(
                message = "Duplicate message blocked to prevent accidental SMS spamming."
            )
        }

        return SendStatus.Success
    }

    @Synchronized
    fun recordSend(recipientKey: String, body: String) {
        val now = System.currentTimeMillis()
        lastGlobalSendTime = now
        sendTimestamps.add(now)
        recentMessagesMap[recipientKey] = StringAndTimestamp(body.trim(), now)
    }
}
