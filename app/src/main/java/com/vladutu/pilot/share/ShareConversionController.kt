package com.vladutu.pilot.share

import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the share UI is currently showing while a Maps link is being converted.
 *
 * [Working] is the invisible fast path (in-app resolve, or papko's first attempt) — the translucent
 * [ShareReceiverActivity] renders nothing. [Retrying] appears only after papko's first attempt
 * fails, and drives the visible "Converting… attempt N [Stop]" card.
 */
sealed interface ConversionUiState {
    data object Working : ConversionUiState

    /**
     * @property label   the destination label for the card (share subject, or a generic fallback).
     * @property attempt number of papko attempts **already made** (≥1 once visible). The card shows
     *                   the attempt about to run, i.e. `attempt + 1`.
     */
    data class Retrying(val label: String, val attempt: Int) : ConversionUiState
}

/**
 * Terminal result of [ShareConversionController.run]. The activity acts on it, then finishes.
 */
sealed interface ConversionOutcome {
    /** A Waze deep link is ready; hand it to [ShareIngestService] for headless save + publish. */
    data class Resolved(
        val wazeUrl: String,
        val googleMapsUrl: String,
        val titleSourceUrl: String,
        val provisionalTitle: String?,
    ) : ConversionOutcome

    /** Not a Maps share (YT Music / Waze / unrecognized) — hand the raw text to the full ingest. */
    data class DelegateToService(
        val rawUrl: String,
        val subject: String?,
    ) : ConversionOutcome
}

/**
 * Drives a shared Maps link through resolution, surfacing a foreground retry loop when it has to
 * fall back to the flaky `waze.papko.org` converter.
 *
 * Flow (see `docs/superpowers/specs/2026-06-14-maps-conversion-retry-loop-design.md`):
 *  1. Non-Maps share → [ConversionOutcome.DelegateToService] (no UI).
 *  2. In-app resolver succeeds → [ConversionOutcome.Resolved] (no UI).
 *  3. Else loop the converter every [retryDelayMs] until it returns a Waze URL — emitting
 *     [ConversionUiState.Retrying] (visible card) after each failure. Runs until success or the
 *     caller cancels the coroutine (user pressed Stop or left Pilot).
 *
 * Pure of Android dependencies so it is unit-testable with `kotlinx-coroutines-test` virtual time.
 */
class ShareConversionController(
    private val inAppResolver: MapsResolver,
    private val converter: WazeConverter,
    private val retryDelayMs: Long = 1_000L,
) {
    private val _state = MutableStateFlow<ConversionUiState>(ConversionUiState.Working)
    val state: StateFlow<ConversionUiState> = _state.asStateFlow()

    suspend fun run(rawUrl: String, subject: String?): ConversionOutcome {
        val classified = UrlClassifier.classifyUrl(rawUrl, subject)
        if (classified !is ClassifiedShare.MapsShare) {
            DiagnosticLog.i(TAG, "not a Maps share (${classified?.let { it::class.simpleName } ?: "unrecognized"}) — delegating")
            return ConversionOutcome.DelegateToService(rawUrl, subject)
        }

        // Primary: on-device resolution. The subject often already carries the coords as a hint.
        val inApp = inAppResolver.resolve(
            classified.rawUrl,
            hints = listOfNotNull(classified.provisionalTitle),
        )
        if (inApp != null) {
            DiagnosticLog.i(TAG, "in-app resolved without papko")
            return ConversionOutcome.Resolved(
                wazeUrl = inApp.wazeUrl,
                googleMapsUrl = classified.rawUrl,
                titleSourceUrl = inApp.resolvedUrl,
                provisionalTitle = classified.provisionalTitle,
            )
        }

        // Fallback: papko, retried every retryDelayMs until it answers or we're cancelled.
        val label = classified.provisionalTitle?.takeIf { it.isNotBlank() } ?: DEFAULT_LABEL
        var attempts = 0
        while (true) {
            try {
                val wazeUrl = converter.convert(classified.rawUrl)
                DiagnosticLog.i(TAG, "papko converted after ${attempts + 1} attempt(s)")
                return ConversionOutcome.Resolved(
                    wazeUrl = wazeUrl,
                    googleMapsUrl = classified.rawUrl,
                    // papko returns only the Waze link, not a resolved /place/ URL — title falls
                    // back to the subject (provisionalTitle), then the raw URL.
                    titleSourceUrl = classified.rawUrl,
                    provisionalTitle = classified.provisionalTitle,
                )
            } catch (e: WazeConversionException) {
                attempts++
                DiagnosticLog.w(TAG, "papko attempt $attempts failed: ${e.message}")
                _state.value = ConversionUiState.Retrying(label, attempts)
                delay(retryDelayMs)
            }
        }
    }

    private companion object {
        const val TAG = "ShareConversion"
        const val DEFAULT_LABEL = "this location"
    }
}
