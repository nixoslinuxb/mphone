package com.mphone.data

object CorporateCallerIdRegistry {

    // Pre-indexed registry of Turkish Banks, Telecom, Logistics, and Official Corporate 0850 / 444 Numbers
    private val CORPORATE_NUMBERS_MAP = mapOf(
        // Banks
        "08502220000" to "Garanti BBVA",
        "4440333" to "Garanti BBVA",
        "08502205000 " to "Ziraat Katılım",
        "4444477" to "Vakıf Katılım",
        "08503400340" to "Papara",
        "08502223663" to "Enpara.com",
        "08507240724 " to "İş bankası",

        // Telecom Operators
        "4441444" to "Türk Telekom",
        "4448875" to "TürkNet",

        // Shopping sites
        "08502524000" to "Hepsiburada",
        "08503330011" to "N11",
        "02123310200" to "Trendyol",
        "08502023350" to "Horoz Lojistik",

    )

    fun getCorporateName(number: String): String? {
        val cleanNumber = ContactMerger.normalizePhoneNumber(number)
        if (cleanNumber.isBlank()) return null

        // Exact match
        CORPORATE_NUMBERS_MAP[cleanNumber]?.let { return it }

        // Suffix / Prefix Match for 0850 / 444 numbers
        CORPORATE_NUMBERS_MAP.forEach { (corpNum, name) ->
            val cleanCorp = ContactMerger.normalizePhoneNumber(corpNum)
            if (cleanNumber == cleanCorp || (cleanNumber.length >= 7 && cleanCorp.length >= 7 && cleanNumber.takeLast(7) == cleanCorp.takeLast(7))) {
                return name
            }
        }

        return null
    }
}
