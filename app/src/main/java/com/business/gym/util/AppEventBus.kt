package com.business.gym.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AppEventBus {
    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    suspend fun emit(event: String) {
        _events.emit(event)
    }
}
