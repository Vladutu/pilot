package com.vladutu.pilot.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.vladutu.pilot.PilotApp
import kotlinx.coroutines.launch

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val urlText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (urlText.isNullOrBlank()) {
            Toast.makeText(this, "Not a recognized link", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val app = application as PilotApp

        app.applicationScope.launch {
            val result = app.destinationPipeline.ingest(
                urlText = urlText,
                manualTitle = null,
                subject = subject,
            )
            showToastFromAppContext(result.toastText)
        }
        Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show()
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
}
