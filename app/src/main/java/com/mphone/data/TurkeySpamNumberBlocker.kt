package com.mphone.data

object TurkeySpamNumberBlocker {

    // High-risk VoIP & Robocall prefix blocks commonly reported in Türkiye
    private val spamPrefixes = listOf(
        // +90312 / 0312 / 90312 Ankara Fixed Line Call Center Spam Prefix Block
        "+90312", "0312", "90312", "312",

        // 0850 Robocall & Illegal Betting / Fake Fiber Internet Scam Ranges
        "0850480", "0850484", "0850549", "0850252", "0850399", "0850473",
        "0850901", "0850811", "+90850480", "+90850484", "+90850549", "+90850252",
        "+90850399", "+90850473", "+90850901", "+90850811",

        // 0212 & 0216 Virtual Call Center Spam Ranges
        "0212908", "0212922", "0212945", "0212988", "0216237", "0216990",
        "+90212908", "+90212922", "+90212945", "+90212988", "+90216237", "+90216990"
    )

    // Specifically reported Turkish spam / scam numbers
    private val reportedSpamNumbers = setOf(
        "+908504840000", "+908504841111", "+908505490000", "+908504801122",
        "+902129080000", "+902129220000", "+908502522222", "+902162370000",
        "08504840000", "08505490000", "02129080000", "02129080000"
    )

    // Comprehensive Illegal Betting, Casino, Slot & Gambling SMS Keywords
    private val bettingKeywords = listOf(
        "deneme bonusu", "free spin", "freespin", "canlı bahis", "casino",
        "yatırım bonusu", "hoşgeldin bonusu", "çevrimsiz bonus", "çevrimsiz",
        "kayıp bonusu", "giriş adresi", "güncel giriş", "tıkla giriş",
        "papara", "payfix", "mefete", "bahis", "bet", "slot", "poker",
        "rulet", "blackjack", "baccarat", "aviator", "sweet bonanza",
        "1xbet", "bettilt", "matadorbet", "grandpashabet", "süperbahis",
        "mobilbahis", "bets10", "tempobet", "sekabet", "restbet",
        "betebet", "tipobet", "jojobet", "mariobet", "sahabet",
        "betwoon", "pokerklas", "pashagaming", "casinomaxi", "discountcasino"
    )

    // General Phishing & Telemarketing Scam Keywords
    private val phishingKeywords = listOf(
        "taahhüdünüz sona eriyor", "yasal takip başlatılmıştır", "icra takibi",
        "kargonuz teslim edilemedi", "paket yenileme", "internet taahhüt"
    )

    /**
     * Checks if a number belongs to known Turkish telemarketing, illegal betting, or robocall spam lists/ranges.
     * Includes automatic blocking for all +90312 / 0312 numbers.
     */
    fun isTurkishSpamNumber(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
        if (cleanNumber.isBlank()) return false

        // Explicit check for Ankara +90312 / 0312 spam prefix
        if (cleanNumber.startsWith("+90312") || cleanNumber.startsWith("0312") || cleanNumber.startsWith("90312")) {
            return true
        }

        // Check explicit reported spam numbers
        if (reportedSpamNumbers.contains(cleanNumber)) return true

        // Check high-risk spam prefixes
        for (prefix in spamPrefixes) {
            if (cleanNumber.startsWith(prefix)) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if an SMS message body contains illegal betting, casino, slot, or phishing scam keywords.
     */
    fun containsSpamContent(body: String): Boolean {
        if (body.isBlank()) return false
        val lowerBody = body.lowercase()
        return bettingKeywords.any { lowerBody.contains(it) } ||
                phishingKeywords.any { lowerBody.contains(it) }
    }

    /**
     * Checks if either the sender number or message body matches Turkish betting/spam definitions.
     */
    fun isSpamSms(senderNumber: String, body: String): Boolean {
        return isTurkishSpamNumber(senderNumber) || containsSpamContent(body)
    }

    /**
     * Returns a descriptive reason if the number or message is flagged as spam or betting service.
     */
    fun getSpamBlockReason(phoneNumber: String, body: String? = null): String? {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "").trim()
        if (cleanNumber.startsWith("+90312") || cleanNumber.startsWith("0312") || cleanNumber.startsWith("90312")) {
            return "Blocked: Ankara Landline Spam Prefix (+90312)."
        }
        if (isTurkishSpamNumber(phoneNumber)) {
            return "Blocked: Flagged in Türkiye Spam & Robocall Database ($phoneNumber)."
        }
        if (body != null && containsSpamContent(body)) {
            return "Blocked: Illegal Betting / Casino / Scam SMS Content."
        }
        return null
    }
}
