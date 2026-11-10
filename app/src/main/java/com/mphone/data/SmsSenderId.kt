package com.mphone.data

/**
 * Helpers for detecting SMS senders that are not real phone numbers.
 *
 * Turkish (and most international) operators use alphanumeric sender IDs for
 * commercial / "brand" messages (e.g. "BANK", "TRENDYOL", "PTT"). These IDs are
 * one-way in practice: replying either fails or is silently discarded, so the
 * app must not offer a reply UI for them.
 */
object SmsSenderId {

    /**
     * Returns true when [number] is an alphanumeric sender ID (a brand message)
     * rather than a dialable phone number.
     */
    fun isBrandSenderId(number: String): Boolean {
        val value = number.trim()
        if (value.isBlank()) return false
        if (value.equals("Me", ignoreCase = true) || value.equals("Unknown", ignoreCase = true)) return false

        // Any letter means this is not a plain phone number. Numeric short codes
        // (e.g. "1234") and 444/0850 corporate numbers stay replyable.
        return value.any { it.isLetter() }
    }
}
