package com.mphone.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony

class SmsContentObserver(
    private val context: Context,
    private val onSmsChanged: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var isRegistered = false

    fun register() {
        if (!isRegistered) {
            try {
                context.contentResolver.registerContentObserver(
                    Telephony.Sms.CONTENT_URI,
                    true,
                    this
                )
                isRegistered = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun unregister() {
        if (isRegistered) {
            try {
                context.contentResolver.unregisterContentObserver(this)
                isRegistered = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        onSmsChanged()
    }
}
