package com.mphone.data

object CountryCodeFilter {

    /**
     * Checks if a given phone number originates from outside of Türkiye.
     * Turkish country code is +90 (or 0090).
     * Local numbers (e.g., starting with 05xx, 5xx, or shortcodes) are treated as domestic Turkish numbers.
     * International numbers starting with any other country code (+1, +44, +49, +33, 001, etc.) are detected as outside Türkiye.
     */
    fun isFromOutsideTurkey(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "").trim()

        if (cleanNumber.isBlank()) return false

        // International format starting with +
        if (cleanNumber.startsWith("+")) {
            return !cleanNumber.startsWith("+90")
        }

        // International format starting with 00
        if (cleanNumber.startsWith("00")) {
            return !cleanNumber.startsWith("0090")
        }

        // Domestic Turkish formats (05xx, 5xx, shortcodes, sender IDs) -> Domestic
        return false
    }

    /**
     * Returns a human readable reason for blocking if the number is from outside Türkiye.
     */
    fun getBlockReason(phoneNumber: String): String? {
        if (isFromOutsideTurkey(phoneNumber)) {
            val countryPrefix = extractCountryPrefix(phoneNumber)
            return "Blocked automatically: Number originates outside of Türkiye ($countryPrefix)."
        }
        return null
    }

    private fun extractCountryPrefix(phoneNumber: String): String {
        val clean = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
        return when {
            clean.startsWith("+") -> clean.take(4)
            clean.startsWith("00") -> "+${clean.drop(2).take(3)}"
            else -> "International"
        }
    }
}
