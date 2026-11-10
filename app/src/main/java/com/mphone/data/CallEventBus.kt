package com.mphone.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class CallEvent {
    data object Connected : CallEvent()
    data object Ended : CallEvent()
}

object CallEventBus {
    private val _callStateEvent = MutableSharedFlow<CallEvent>(extraBufferCapacity = 10)
    val callStateEvent: SharedFlow<CallEvent> = _callStateEvent.asSharedFlow()

    fun emitCallConnected() {
        _callStateEvent.tryEmit(CallEvent.Connected)
    }

    fun emitCallEnded() {
        _callStateEvent.tryEmit(CallEvent.Ended)
    }
}
