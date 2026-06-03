package com.vladutu.pilot.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.vladutu.pilot.PilotApp
import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.launch

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

        val app = application as PilotApp

        app.applicationScope.launch {
            DiagnosticLog.i(TAG, "ingest coroutine started")
            val result = try {
                app.destinationPipeline.ingest(
                    urlText = urlText,
                    manualTitle = null,
                    subject = subject,
                )
            } catch (t: Throwable) {
                DiagnosticLog.e(TAG, "ingest threw ${t.javaClass.simpleName}", t)
                throw t
            }
            DiagnosticLog.i(TAG, "ingest result=${result::class.simpleName} toast=${result.toastText}")
            showToastFromAppContext(result.toastText)
        }
        Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()
        DiagnosticLog.i(TAG, "finish() — activity destroyed, coroutine continues on applicationScope")
        finish()
    }

    private fun showToastFromAppContext(text: String) {
        val app = application as PilotApp
        app.applicationScope.launch {
            android.os.Handler(mainLooper).post {
                Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private companion object {
        const val TAG = "Share"
    }
}
