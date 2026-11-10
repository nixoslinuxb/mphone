package com.mphone.data

import com.mphone.model.Contact

object ContactMerger {

    fun normalizePhoneNumber(number: String): String {
        if (number.isBlank()) return ""
        var cleaned = number.replace(Regex("[^0-9+]"), "")
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2)
        }
        if (cleaned.startsWith("0") && cleaned.length == 11) {
            cleaned = "+90" + cleaned.substring(1)
        }
        if (!cleaned.startsWith("+") && cleaned.length == 10) {
            cleaned = "+90$cleaned"
        }
        return cleaned
    }

    fun findContactForNumber(contacts: List<Contact>, number: String): Contact? {
        val cleanTarget = normalizePhoneNumber(number)
        if (cleanTarget.isBlank()) return null

        return contacts.find { contact ->
            val cleanContact = normalizePhoneNumber(contact.phoneNumber)
            cleanContact == cleanTarget ||
                    (cleanTarget.length >= 7 && cleanContact.length >= 7 && cleanContact.takeLast(7) == cleanTarget.takeLast(7))
        }
    }

    /**
     * Merges duplicate contacts in a list that share the same normalized phone number.
     */
    fun mergeDuplicateContacts(contacts: List<Contact>): List<Contact> {
        if (contacts.isEmpty()) return emptyList()

        val grouped = contacts.groupBy { normalizePhoneNumber(it.phoneNumber) }
        val mergedList = mutableListOf<Contact>()

        grouped.forEach { (_, contactGroup) ->
            val bestName = contactGroup
                .map { it.name }
                .filter { it.isNotBlank() && it != "Unknown" }
                .maxByOrNull { it.length } ?: contactGroup.first().name

            val bestPhotoUri = contactGroup
                .mapNotNull { it.photoUri?.ifBlank { null } }
                .sortedByDescending { if (it.startsWith("file://")) 2 else 1 }
                .firstOrNull()

            val combinedNotes = contactGroup
                .map { it.note.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString("\n")

            val primaryContact = contactGroup.first()

            mergedList.add(
                Contact(
                    id = primaryContact.id,
                    name = bestName,
                    phoneNumber = primaryContact.phoneNumber,
                    photoUri = bestPhotoUri,
                    avatarColorIndex = primaryContact.avatarColorIndex,
                    note = combinedNotes.ifBlank { primaryContact.note }
                )
            )
        }

        return mergedList.distinctBy { normalizePhoneNumber(it.phoneNumber).ifBlank { it.id } }
    }
}
