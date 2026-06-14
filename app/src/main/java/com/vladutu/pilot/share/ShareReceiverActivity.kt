package com.vladutu.pilot.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vladutu.pilot.PilotApp
import com.vladutu.pilot.diagnostics.DiagnosticLog
import com.vladutu.pilot.ui.theme.PilotTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Receives a SEND share, resolves a Maps link to Waze on-device, and (only when it must fall back
 * to the flaky papko converter and that first attempt fails) shows a foreground retry loop instead
 * of failing on the spot. See `docs/superpowers/specs/2026-06-14-maps-conversion-retry-loop-design.md`.
 *
 * The activity uses a translucent theme, so while [ShareConversionController] is on its fast path
 * (in-app resolve, or papko's first try) nothing is drawn — it feels exactly like the old
 * fire-and-forget handoff. The "Converting… attempt N [Stop]" card appears only on a papko failure.
 *
 * The retry loop runs in [scope], which is cancelled in [onDestroy]; leaving Pilot or pressing Stop
 * finishes the activity, which cancels the loop. Once a Waze URL is in hand, save + publish is
 * handed to [ShareIngestService] (foreground) so it completes even if the user immediately leaves.
 *
 * `android:configChanges` in the manifest keeps this activity (and thus the loop) alive across
 * rotation — the project has no ViewModel layer, so we hold the loop in the activity scope.
 */
class ShareReceiverActivity : ComponentActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** True once we've finished for a real reason (handoff or user Stop), so [onStop] doesn't double-handle. */
    private var handedOff = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DiagnosticLog.i(TAG, "onCreate action=${intent.action} type=${intent.type}")

        val urlText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (urlText.isNullOrBlank()) {
            DiagnosticLog.w(TAG, "missing or blank EXTRA_TEXT — finishing")
            Toast.makeText(this, "Not a recognized link", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        DiagnosticLog.i(TAG, "urlText=$urlText subject=$subject")

        val app = application as PilotApp
        val controller = ShareConversionController(
            inAppResolver = app.inAppMapsResolver,
            converter = app.mapsToWazeConverter,
        )

        setContent {
            val state by controller.state.collectAsState()
            // Nothing composes on the fast path → the translucent window stays fully transparent
            // (and PilotTheme's status-bar SideEffect doesn't fire). The card appears only on retry.
            val current = state
            if (current is ConversionUiState.Retrying) {
                PilotTheme {
                    ConversionOverlay(state = current, onStop = ::onUserStop)
                }
            }
        }

        scope.launch {
            val outcome = controller.run(urlText, subject)
            handOff(outcome, subject)
        }
    }

    /** A Waze URL (or a non-Maps share) is ready — hand to the service for headless save + publish. */
    private fun handOff(outcome: ConversionOutcome, subject: String?) {
        handedOff = true
        val serviceIntent = when (outcome) {
            is ConversionOutcome.Resolved -> ShareIngestService.intentResolved(
                context = this,
                wazeUrl = outcome.wazeUrl,
                googleMapsUrl = outcome.googleMapsUrl,
                titleSourceUrl = outcome.titleSourceUrl,
                subject = outcome.provisionalTitle ?: subject,
            )
            is ConversionOutcome.DelegateToService -> ShareIngestService.intent(
                context = this,
                urlText = outcome.rawUrl,
                subject = outcome.subject,
            )
        }
        // We're still foreground here, so startForegroundService → startForeground is permitted.
        startForegroundService(serviceIntent)
        Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()
        DiagnosticLog.i(TAG, "handed off (${outcome::class.simpleName}) — finishing")
        finish()
    }

    /** User pressed Stop on the retry card — silently drop the share. */
    private fun onUserStop() {
        DiagnosticLog.i(TAG, "user stopped retry loop")
        handedOff = true
        finish()
    }

    override fun onStop() {
        super.onStop()
        // Leaving Pilot mid-conversion (Home, app switch, back) cancels the loop — same as Stop.
        // Skip our own finish-after-handoff and rotation (configChanges keeps us alive; guard anyway).
        if (!handedOff && !isChangingConfigurations) {
            DiagnosticLog.i(TAG, "left during conversion — stopping")
            handedOff = true
            finish()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Share"
    }
}
