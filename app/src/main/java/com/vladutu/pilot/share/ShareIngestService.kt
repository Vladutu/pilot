package com.vladutu.pilot.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import com.vladutu.pilot.PilotApp
import com.vladutu.pilot.R
import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs the share ingest as a foreground service.
 *
 * Why a foreground service: the share work (metadata fetch + ntfy publish) used to run on a bare
 * background coroutine after [ShareReceiverActivity] called finish(). By then the process is CACHED
 * (backgrounded), and on **metered** networks (mobile data) Android starves a backgrounded process
 * of network — DNS comes back EAI_NODATA for the whole process, so both the artwork fetch and the
 * publish fail. WiFi (unmetered) is exempt, which is why it only happened on cellular. A foreground
 * service keeps the process foreground for the duration, so Android grants it metered network.
 *
 * Started from [ShareReceiverActivity] while that activity is itself foreground, so the
 * startForegroundService → startForeground handoff is allowed even on Android 14.
 */
class ShareIngestService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** In-flight ingests; the service stops itself once this returns to zero. */
    private val inFlight = AtomicInteger(0)

    @Volatile private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        startAsForeground()

        val urlText = intent?.getStringExtra(EXTRA_URL)
        val subject = intent?.getStringExtra(EXTRA_SUBJECT)
        val wazeUrl = intent?.getStringExtra(EXTRA_WAZE_URL)

        // Two entry shapes: the resolved path (EXTRA_WAZE_URL set — the Maps share already went
        // through ShareConversionController in the activity, so we only save + publish) and the
        // full path (EXTRA_URL set — YT Music / Waze / manual, classified + resolved here).
        if (wazeUrl.isNullOrBlank() && urlText.isNullOrBlank()) {
            DiagnosticLog.w(TAG, "missing EXTRA_WAZE_URL/EXTRA_URL — stopping")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        inFlight.incrementAndGet()
        val app = application as PilotApp
        scope.launch {
            DiagnosticLog.i(TAG, "ingest coroutine started (startId=$startId)")
            try {
                val result = if (!wazeUrl.isNullOrBlank()) {
                    app.destinationPipeline.ingestResolvedDestination(
                        wazeUrl = wazeUrl,
                        googleMapsUrl = intent.getStringExtra(EXTRA_GMAPS_URL),
                        provisionalTitle = subject,
                        titleSourceUrl = intent.getStringExtra(EXTRA_TITLE_SOURCE_URL) ?: wazeUrl,
                    )
                } else {
                    app.destinationPipeline.ingest(
                        urlText = urlText!!,
                        manualTitle = null,
                        subject = subject,
                    )
                }
                DiagnosticLog.i(TAG, "ingest result=${result::class.simpleName} toast=${result.toastText}")
                showToast(result.toastText)
            } catch (t: Throwable) {
                DiagnosticLog.e(TAG, "ingest threw ${t.javaClass.simpleName}", t)
                showToast("Send failed")
            } finally {
                if (inFlight.decrementAndGet() == 0) {
                    DiagnosticLog.i(TAG, "no ingests in flight — stopping service")
                    // stopSelf(id) is a no-op if a newer start arrived meanwhile, so we never
                    // tear down with work still queued.
                    stopSelf(lastStartId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification: Notification = Notification.Builder(this, ensureChannel())
            .setContentTitle("Sending to car…")
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun ensureChannel(): String {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sending to car", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return CHANNEL_ID
    }

    private fun showToast(text: String) {
        val app = applicationContext
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "ShareSvc"
        private const val CHANNEL_ID = "share_ingest"
        private const val NOTIF_ID = 1001
        const val EXTRA_URL = "com.vladutu.pilot.share.EXTRA_URL"
        const val EXTRA_SUBJECT = "com.vladutu.pilot.share.EXTRA_SUBJECT"
        const val EXTRA_WAZE_URL = "com.vladutu.pilot.share.EXTRA_WAZE_URL"
        const val EXTRA_GMAPS_URL = "com.vladutu.pilot.share.EXTRA_GMAPS_URL"
        const val EXTRA_TITLE_SOURCE_URL = "com.vladutu.pilot.share.EXTRA_TITLE_SOURCE_URL"

        /** Full path: classify + resolve + save + publish (YT Music / Waze / manual entry). */
        fun intent(context: Context, urlText: String, subject: String?): Intent =
            Intent(context, ShareIngestService::class.java).apply {
                putExtra(EXTRA_URL, urlText)
                putExtra(EXTRA_SUBJECT, subject)
            }

        /**
         * Resolved path: the Maps share was already converted to [wazeUrl] by
         * [ShareConversionController]; the service only saves + publishes. [subject] carries the
         * provisional title (share subject); [titleSourceUrl] is the resolved /place/ URL when
         * available (in-app), else the raw Maps URL.
         */
        fun intentResolved(
            context: Context,
            wazeUrl: String,
            googleMapsUrl: String?,
            titleSourceUrl: String?,
            subject: String?,
        ): Intent =
            Intent(context, ShareIngestService::class.java).apply {
                putExtra(EXTRA_WAZE_URL, wazeUrl)
                putExtra(EXTRA_GMAPS_URL, googleMapsUrl)
                putExtra(EXTRA_TITLE_SOURCE_URL, titleSourceUrl)
                putExtra(EXTRA_SUBJECT, subject)
            }
    }
}
