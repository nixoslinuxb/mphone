package com.mphone.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.mphone.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ContactsRepository(private val context: Context) {
    private val _contacts = MutableStateFlow<List<Contact>>(
        ContactMerger.mergeDuplicateContacts(MockData.sampleContacts)
    )
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val prefs = context.getSharedPreferences("mphone_deleted_contacts_prefs", Context.MODE_PRIVATE)
    private val notesPrefs = context.getSharedPreferences("mphone_contact_notes_prefs", Context.MODE_PRIVATE)

    init {
        try {
            val cached = ContactsLocalCache.loadContacts(context)
            if (cached.isNotEmpty()) {
                val deletedKeys = getDeletedContactKeys()
                val filteredCached = cached.filter {
                    !deletedKeys.contains(it.id) && !deletedKeys.contains(ContactMerger.normalizePhoneNumber(it.phoneNumber))
                }.map { contact ->
                    val cleanNum = ContactMerger.normalizePhoneNumber(contact.phoneNumber)
                    val savedNote = getSavedContactNote(cleanNum, contact.id)
                    if (savedNote.isNotBlank() && contact.note.isBlank()) {
                        contact.copy(note = savedNote)
                    } else {
                        contact
                    }
                }
                if (filteredCached.isNotEmpty()) {
                    _contacts.value = ContactMerger.mergeDuplicateContacts(filteredCached)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSavedContactNote(cleanNumber: String, contactId: String = ""): String {
        val noteByNumber = if (cleanNumber.isNotBlank()) notesPrefs.getString("note_num_$cleanNumber", "") ?: "" else ""
        if (noteByNumber.isNotBlank()) return noteByNumber
        val noteById = if (contactId.isNotBlank()) notesPrefs.getString("note_id_$contactId", "") ?: "" else ""
        return noteById
    }

    fun saveContactNoteLocally(cleanNumber: String, contactId: String, note: String) {
        val editor = notesPrefs.edit()
        val trimmedNote = note.trim()
        if (cleanNumber.isNotBlank()) {
            editor.putString("note_num_$cleanNumber", trimmedNote)
        }
        if (contactId.isNotBlank()) {
            editor.putString("note_id_$contactId", trimmedNote)
        }
        editor.apply()
    }

    private fun persistContactsState(list: List<Contact>) {
        try {
            ContactsLocalCache.saveContacts(context, list)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDeletedContactKeys(): Set<String> {
        return try {
            prefs.getStringSet("deleted_contacts", emptySet()) ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    fun isContactDeleted(number: String): Boolean {
        val clean = ContactMerger.normalizePhoneNumber(number)
        if (clean.isBlank()) return false
        return getDeletedContactKeys().contains(clean)
    }

    private fun markContactDeleted(cleanNumber: String, contactId: String) {
        try {
            val set = getDeletedContactKeys().toMutableSet()
            if (cleanNumber.isNotBlank()) set.add(cleanNumber)
            if (contactId.isNotBlank()) set.add(contactId)
            prefs.edit().putStringSet("deleted_contacts", set).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun markContactRestored(cleanNumber: String) {
        try {
            if (cleanNumber.isBlank()) return
            val set = getDeletedContactKeys().toMutableSet()
            set.remove(cleanNumber)
            prefs.edit().putStringSet("deleted_contacts", set).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadContacts() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val deletedKeys = getDeletedContactKeys()

        if (!hasPermission) {
            val baseList = _contacts.value.ifEmpty { MockData.sampleContacts }
            _contacts.value = ContactMerger.mergeDuplicateContacts(baseList).filter {
                !deletedKeys.contains(it.id) && !deletedKeys.contains(ContactMerger.normalizePhoneNumber(it.phoneNumber))
            }
            return
        }

        try {
            val list = mutableListOf<Contact>()
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val thumbIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

                var colorSeed = 0
                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getString(idIdx) else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "Unknown" else "Unknown"
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val rawPhotoUri = if (photoIdx >= 0) it.getString(photoIdx) else null
                    val rawThumbUri = if (thumbIdx >= 0) it.getString(thumbIdx) else null
                    val photoUri = rawPhotoUri?.ifBlank { null } ?: rawThumbUri?.ifBlank { null }
                    val cleanNumber = ContactMerger.normalizePhoneNumber(number)

                    if (deletedKeys.contains(id) || deletedKeys.contains(cleanNumber)) {
                        continue
                    }

                    if (number.isNotBlank()) {
                        val savedNote = getSavedContactNote(cleanNumber, id)
                        list.add(
                            Contact(
                                id = id.ifBlank { System.currentTimeMillis().toString() },
                                name = name,
                                phoneNumber = number,
                                photoUri = photoUri,
                                avatarColorIndex = (colorSeed++) % 6,
                                note = savedNote
                            )
                        )
                    }
                }
            }

            // Merge system contact rows with in-app local contacts
            val currentLocal = _contacts.value
            val combined = ContactMerger.mergeDuplicateContacts(list + currentLocal).filter {
                !deletedKeys.contains(it.id) && !deletedKeys.contains(ContactMerger.normalizePhoneNumber(it.phoneNumber))
            }

            if (combined.isNotEmpty()) {
                _contacts.value = combined
                persistContactsState(combined)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val deletedKeys = getDeletedContactKeys()
            _contacts.value = _contacts.value.filter {
                !deletedKeys.contains(it.id) && !deletedKeys.contains(ContactMerger.normalizePhoneNumber(it.phoneNumber))
            }
        }
    }

    fun addContact(name: String, number: String, photoUri: String? = null, note: String = "") {
        val persistentPhotoUri = ContactPhotoStorage.savePhotoLocally(context, photoUri)
        val cleanNumber = ContactMerger.normalizePhoneNumber(number)
        val newId = System.currentTimeMillis().toString()
        val trimmedNote = note.trim()

        markContactRestored(cleanNumber)
        saveContactNoteLocally(cleanNumber, newId, trimmedNote)

        val newContact = Contact(
            id = newId,
            name = name,
            phoneNumber = number,
            photoUri = persistentPhotoUri,
            avatarColorIndex = (0..5).random(),
            note = trimmedNote
        )

        // 1. Insert into Android's native system Contacts database
        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasWritePermission) {
            try {
                val ops = ArrayList<ContentProviderOperation>()
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                        .build()
                )

                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                        .build()
                )

                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, number)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                        .build()
                )

                if (trimmedNote.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.Note.NOTE, trimmedNote)
                            .build()
                    )
                }

                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Update local state and save to local disk cache
        val combined = listOf(newContact) + _contacts.value
        val merged = ContactMerger.mergeDuplicateContacts(combined)
        _contacts.value = merged
        persistContactsState(merged)
    }

    fun updateContact(contactId: String, name: String, number: String, photoUri: String? = null, note: String = "") {
        val persistentPhotoUri = ContactPhotoStorage.savePhotoLocally(context, photoUri)
        val cleanNumber = ContactMerger.normalizePhoneNumber(number)
        val trimmedNote = note.trim()

        saveContactNoteLocally(cleanNumber, contactId, trimmedNote)

        val updated = _contacts.value.map { contact ->
            val cleanExisting = ContactMerger.normalizePhoneNumber(contact.phoneNumber)
            if (contact.id == contactId || (cleanNumber.isNotBlank() && cleanExisting == cleanNumber)) {
                contact.copy(
                    name = name,
                    phoneNumber = number,
                    photoUri = persistentPhotoUri,
                    note = trimmedNote
                )
            } else {
                contact
            }
        }

        val mergedUpdated = ContactMerger.mergeDuplicateContacts(updated).map { contact ->
            val cleanNum = ContactMerger.normalizePhoneNumber(contact.phoneNumber)
            val savedNote = getSavedContactNote(cleanNum, contact.id)
            if (savedNote.isNotBlank()) {
                contact.copy(note = savedNote)
            } else {
                contact
            }
        }

        _contacts.value = mergedUpdated
        persistContactsState(mergedUpdated)

        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasWritePermission && cleanNumber.isNotBlank()) {
            try {
                val ops = ArrayList<ContentProviderOperation>()
                if (trimmedNote.isNotBlank()) {
                    ops.add(
                        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection(
                                "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                                arrayOf(ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, "%$cleanNumber%")
                            )
                            .withValue(ContactsContract.CommonDataKinds.Note.NOTE, trimmedNote)
                            .build()
                    )
                    context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteContact(contactId: String, phoneNumber: String) {
        val cleanNumber = ContactMerger.normalizePhoneNumber(phoneNumber)
        markContactDeleted(cleanNumber, contactId)

        val remaining = _contacts.value.filter {
            it.id != contactId && ContactMerger.normalizePhoneNumber(it.phoneNumber) != cleanNumber
        }
        _contacts.value = remaining
        persistContactsState(remaining)

        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasWritePermission && cleanNumber.isNotBlank()) {
            try {
                val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                context.contentResolver.delete(
                    uri,
                    "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                    arrayOf("%$cleanNumber%")
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
