package com.vladutu.pilot.net

import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class NtfyPublishException(message: String, cause: Throwable? = null) : IOException(message, cause)

open class NtfyPublisher(
    private val client: OkHttpClient,
    private val base: String,
    private val topicProvider: () -> String?,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
    private val maxRetries: Int = 3,
    private val retryBaseDelayMs: Long = 1_000L,
) {
    private val json = "application/json".toMediaType()

    open suspend fun publishYtMusic(form: Form, id: String, title: String?, imageUrl: String?) {
        require(form == Form.PLAYLIST || form == Form.SONG) {
            "publishYtMusic only accepts PLAYLIST or SONG, got $form"
        }
        postEnvelope(
            cmd = "ytmusic",
            formWire = form.wire,
            url = ytMusicUrl(form, id),
            title = title,
            imageUrl = imageUrl,
        )
    }

    open suspend fun publishWaze(url: String, title: String?) {
        postEnvelope(
            cmd = "waze",
            formWire = Form.DESTINATION.wire,
            url = url,
            title = title,
            imageUrl = null,
        )
    }

    open suspend fun publishMaps(url: String, title: String?) {
        postEnvelope(
            cmd = "maps",
            formWire = Form.DESTINATION.wire,
            url = url,
            title = title,
            imageUrl = null,
        )
    }

    open suspend fun publishRadio(streamUrl: String, title: String?, imageUrl: String?) {
        postEnvelope(
            cmd = "radio",
            formWire = Form.RADIO.wire,
            url = streamUrl,
            title = title,
            imageUrl = imageUrl,
        )
    }

    /**
     * Sync one Discover keyword to the headunit. Delivery is live-only by design
     * (Copilot's 30s staleness window): if the car is off, re-send by tapping the
     * category in the Discover list.
     */
    open suspend fun publishCategory(keyword: String) {
        postEnvelope(cmd = "category", formWire = "category", url = null, title = keyword, imageUrl = null)
    }

    private suspend fun postEnvelope(
        cmd: String,
        formWire: String,
        url: String?,
        title: String?,
        imageUrl: String?,
    ) = withContext(Dispatchers.IO) {
        val topic = topicProvider()?.takeIf { it.isNotBlank() }
        if (topic == null) {
            DiagnosticLog.w(TAG, "publish blocked: not paired (no topic) cmd=$cmd")
            throw NtfyPublishException("not paired: no ntfy topic set")
        }

        val payload = JSONObject().apply {
            put("v", SCHEMA_VERSION)
            put("ts", clock())
            put("cmd", cmd)
            put("form", formWire)
            url?.let { put("url", it) }
            title?.let { put("title", it) }
            imageUrl?.let { put("imageUrl", it) }
        }.toString()

        val req = Request.Builder()
            .url("$base/$topic")
            .header("Title", "Copilot")
            .post(payload.toRequestBody(json))
            .build()

        DiagnosticLog.i(TAG, "publishing cmd=$cmd form=$formWire url=$url")
        executeWithRetry(req)
    }

    /**
     * Sends [req], retrying transient network failures (DNS hiccups, dropped connections, timeouts)
     * with exponential backoff. A blip that resolves a second later — the common mobile case —
     * succeeds silently on the next attempt.
     *
     * Two things callers can rely on:
     *  - Non-2xx HTTP responses are a deliberate signal and are NOT retried.
     *  - Every failure surfaces as [NtfyPublishException]. Raw [IOException]s (e.g.
     *    [java.net.UnknownHostException]) are wrapped, so a single `catch (NtfyPublishException)`
     *    covers all failure modes instead of letting a raw network error escape as an uncaught crash.
     */
    private suspend fun executeWithRetry(req: Request) {
        var attempt = 0
        while (true) {
            try {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        DiagnosticLog.w(TAG, "publish non-2xx HTTP ${resp.code}")
                        throw NtfyPublishException("ntfy returned HTTP ${resp.code}")
                    }
                    DiagnosticLog.i(TAG, "publish ok HTTP ${resp.code}")
                }
                return
            } catch (e: IOException) {
                // Deliberate non-2xx signal — surface immediately, don't retry.
                if (e is NtfyPublishException) throw e
                attempt++
                if (attempt > maxRetries) {
                    DiagnosticLog.e(
                        TAG,
                        "publish failed after $attempt attempts: ${e.javaClass.simpleName}: ${e.message}",
                        e,
                    )
                    throw NtfyPublishException("publish failed after $attempt attempts: ${e.message}", e)
                }
                val backoffMs = retryBaseDelayMs shl (attempt - 1)
                DiagnosticLog.w(
                    TAG,
                    "publish attempt $attempt failed (${e.javaClass.simpleName}: ${e.message}), " +
                        "retrying in ${backoffMs}ms",
                )
                delay(backoffMs)
            }
        }
    }

    companion object {
        private const val TAG = "Ntfy"
        private const val SCHEMA_VERSION = 3

        fun ytMusicUrl(form: Form, id: String): String = when (form) {
            Form.PLAYLIST -> "https://music.youtube.com/watch?list=$id&shuffle=1"
            Form.SONG -> "https://music.youtube.com/watch?v=$id"
            Form.DESTINATION -> throw IllegalArgumentException(
                "DESTINATION is not a YouTube Music form; use publishWaze",
            )
            Form.RADIO -> throw IllegalArgumentException(
                "RADIO is not a YouTube Music form; use publishRadio",
            )
        }
    }
}
