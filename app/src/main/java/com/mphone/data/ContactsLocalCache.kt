package com.mphone.data

import android.content.Context
import com.mphone.model.Contact
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ContactsLocalCache {

    private const val FILE_NAME = "mphone_contacts_cache.json"

    fun saveContacts(context: Context, contacts: List<Contact>) {
        try {
            val jsonArray = JSONArray()
            contacts.forEach { contact ->
                val obj = JSONObject().apply {
                    put("id", contact.id)
                    put("name", contact.name)
                    put("phoneNumber", contact.phoneNumber)
                    put("photoUri", contact.photoUri ?: "")
                    put("avatarColorIndex", contact.avatarColorIndex)
                    put("note", contact.note)
                }
                jsonArray.put(obj)
            }

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadContacts(context: Context): List<Contact> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        try {
            val jsonStr = file.readText()
            if (jsonStr.isBlank()) return emptyList()

            val list = mutableListOf<Contact>()
            val jsonArray = JSONArray(jsonStr)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "")
                val number = obj.optString("phoneNumber", "")
                val photoUri = obj.optString("photoUri", "").ifBlank { null }
                val avatarColorIndex = obj.optInt("avatarColorIndex", 0)
                val note = obj.optString("note", "")

                if (name.isNotBlank() && number.isNotBlank()) {
                    list.add(
                        Contact(
                            id = id.ifBlank { System.currentTimeMillis().toString() },
                            name = name,
                            phoneNumber = number,
                            photoUri = photoUri,
                            avatarColorIndex = avatarColorIndex,
                            note = note
                        )
                    )
                }
            }
            return list
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
}
