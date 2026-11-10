package com.mphone.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SmsEventBus {
    private val _incomingSmsEvent = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 10)
    val incomingSmsEvent: SharedFlow<Pair<String, String>> = _incomingSmsEvent.asSharedFlow()

    fun emitIncomingSms(sender: String, body: String) {
        _incomingSmsEvent.tryEmit(Pair(sender, body))
    }
}
