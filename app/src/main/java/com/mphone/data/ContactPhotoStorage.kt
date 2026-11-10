package com.mphone.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ContactPhotoStorage {

    /**
     * Saves a temporary Uri (e.g. PhotoPicker content:// Uri) permanently into
     * the app's internal private storage directory so the profile picture is
     * permanently preserved across app closes, process kills, and phone restarts.
     */
    fun savePhotoLocally(context: Context, sourceUriString: String?): String? {
        if (sourceUriString.isNullOrBlank()) return null

        // If it's already a persistent internal file URI, return as-is
        if (sourceUriString.startsWith("file://") && sourceUriString.contains("contact_photos")) {
            return sourceUriString
        }

        try {
            val sourceUri = Uri.parse(sourceUriString)
            val dir = File(context.filesDir, "contact_photos").apply {
                if (!exists()) mkdirs()
            }
            val fileName = "photo_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
            val targetFile = File(dir, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                return Uri.fromFile(targetFile).toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return sourceUriString
    }
}
