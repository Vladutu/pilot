package com.vladutu.pilot.share

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.vladutu.pilot.PilotApp
import com.vladutu.pilot.catalog.CatalogEntry
import com.vladutu.pilot.net.NtfyPublishException
import kotlinx.coroutines.launch

class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val share = UrlClassifier.classify(intent)
        if (share == null) {
            Toast.makeText(this, "Not a YouTube link", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val entry = CatalogEntry(
            form = share.form,
            id = share.id,
            title = share.provisionalTitle ?: "Untitled ${share.id}",
            imagePath = null,
            savedAt = System.currentTimeMillis(),
        )

        val app = application as PilotApp

        app.applicationScope.launch { app.catalogStore.upsert(entry) }
        app.applicationScope.launch {
            try {
                app.ntfyPublisher.publishYtMusic(entry.form, entry.id)
            } catch (e: NtfyPublishException) {
                Log.w(TAG, "ntfy publish failed during share", e)
                showToastFromAppContext("Send failed")
            }
        }
        app.applicationScope.launch {
            try {
                app.metadataFetcher.refresh(share, app.catalogStore)
            } catch (e: Exception) {
                Log.w(TAG, "metadata refresh failed", e)
            }
        }

        Toast.makeText(this, "Saved: ${entry.title}", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Toast from app context — Activity is already finishing when async failures land. */
    private fun showToastFromAppContext(text: String) {
        val app = application as PilotApp
        app.applicationScope.launch {
            // Toasts must run on the main thread.
            android.os.Handler(mainLooper).post {
                Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private companion object {
        const val TAG = "ShareReceiver"
    }
}
