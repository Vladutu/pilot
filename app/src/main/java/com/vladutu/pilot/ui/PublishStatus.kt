package com.vladutu.pilot.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PublishStatus { Unknown, Ok, Failed }

/**
 * Tracks the result of the most recent ntfy publish so the top-bar status pill can
 * surface it. We reflect the last attempt rather than polling — no extra network,
 * and a stale red/green is still useful info before you drive off.
 */
class PublishStatusHolder {
    private val _state = MutableStateFlow(PublishStatus.Unknown)
    val state: StateFlow<PublishStatus> = _state

    fun markOk() { _state.value = PublishStatus.Ok }
    fun markFailed() { _state.value = PublishStatus.Failed }
}
