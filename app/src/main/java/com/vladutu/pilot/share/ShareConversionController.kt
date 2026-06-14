package com.vladutu.pilot.share

import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the share UI is currently showing while a Maps link is being converted.
 *
 * [Working] is the brief invisible window (≤ grace delay) — the translucent [ShareReceiverActivity]
 * renders nothing, so genuinely fast resolves never flash any UI. [Converting] is the spinner card
 * shown once resolution takes longer than the grace delay (so the user sees Pilot working instead of
 * a frozen Maps). [Retrying] is the same card plus an attempt counter, shown once papko has failed
 * at least once.
 */
sealed interface ConversionUiState {
    data object Working : ConversionUiState

    /** Spinner card: resolution is taking a moment (in-app network, or papko's first attempt). */
    data class Converting(val label: String) : ConversionUiState

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
    // How long resolution may run while staying invisible. Fast resolves finish under this and never
    // flash UI; anything slower shows the spinner card so Maps doesn't just look frozen.
    private val graceDelayMs: Long = 450L,
) {
    private val _state = MutableStateFlow<ConversionUiState>(ConversionUiState.Working)
    val state: StateFlow<ConversionUiState> = _state.asStateFlow()

    suspend fun run(rawUrl: String, subject: String?): ConversionOutcome = coroutineScope {
        val classified = UrlClassifier.classifyUrl(rawUrl, subject)
        if (classified !is ClassifiedShare.MapsShare) {
            DiagnosticLog.i(TAG, "not a Maps share (${classified?.let { it::class.simpleName } ?: "unrecognized"}) — delegating")
            return@coroutineScope ConversionOutcome.DelegateToService(rawUrl, subject)
        }

        val label = classified.provisionalTitle?.takeIf { it.isNotBlank() } ?: DEFAULT_LABEL

        // Reveal the spinner card only if resolution outlasts the grace window — keeps the common
        // fast resolve flash-free while making slower fallbacks visibly "working", not frozen.
        val graceJob = launch {
            delay(graceDelayMs)
            if (_state.value is ConversionUiState.Working) {
                _state.value = ConversionUiState.Converting(label)
            }
        }

        try {
            // Primary: on-device resolution. The subject often already carries the coords as a hint.
            val inApp = inAppResolver.resolve(
                classified.rawUrl,
                hints = listOfNotNull(classified.provisionalTitle),
            )
            if (inApp != null) {
                DiagnosticLog.i(TAG, "in-app resolved without papko")
                return@coroutineScope ConversionOutcome.Resolved(
                    wazeUrl = inApp.wazeUrl,
                    googleMapsUrl = classified.rawUrl,
                    titleSourceUrl = inApp.resolvedUrl,
                    provisionalTitle = classified.provisionalTitle,
                )
            }
            // Fallback: papko, retried until it answers or we're cancelled.
            resolveViaPapko(classified, label)
        } finally {
            graceJob.cancel()
        }
    }

    /** Loops the converter every [retryDelayMs] until it returns a Waze URL (or the caller cancels). */
    private suspend fun resolveViaPapko(
        classified: ClassifiedShare.MapsShare,
        label: String,
    ): ConversionOutcome {
        var attempts = 0
        while (true) {
            try {
                val wazeUrl = converter.convert(classified.rawUrl)
                DiagnosticLog.i(TAG, "papko converted after ${attempts + 1} attempt(s)")
                return ConversionOutcome.Resolved(
                    wazeUrl = wazeUrl,
                    googleMapsUrl = classified.rawUrl,
                    // papko returns only the Waze link, not a resolved /place/ URL — title falls back
                    // to the subject (provisionalTitle), then the raw URL.
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
