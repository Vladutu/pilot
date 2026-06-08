package com.vladutu.pilot.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.vladutu.pilot.diagnostics.DiagnosticLog

class ShareReceiverActivity : Activity() {

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
        DiagnosticLog.i(TAG, "urlText=${urlText} subject=${subject}")

        // Hand off to a foreground service so the ingest keeps the process foreground while it does
        // network. A bare background coroutine here gets its metered (mobile-data) network starved
        // once this activity finishes — see ShareIngestService for the full story. We're foreground
        // right now, so starting the foreground service is permitted even on Android 14.
        startForegroundService(ShareIngestService.intent(this, urlText, subject))

        Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()
        DiagnosticLog.i(TAG, "finish() — ingest handed off to ShareIngestService")
        finish()
    }

    private companion object {
        const val TAG = "Share"
    }
}
