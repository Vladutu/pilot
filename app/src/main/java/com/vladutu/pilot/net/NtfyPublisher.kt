package com.vladutu.pilot.net

import com.vladutu.pilot.catalog.Form
import kotlinx.coroutines.Dispatchers
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
    private val topic: String,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val json = "application/json".toMediaType()

    open suspend fun publishYtMusic(form: Form, id: String) {
        postEnvelope(cmd = "ytmusic", url = ytMusicUrl(form, id))
    }

    open suspend fun publishWaze(url: String) {
        postEnvelope(cmd = "waze", url = url)
    }

    private suspend fun postEnvelope(cmd: String, url: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("v", SCHEMA_VERSION)
            put("ts", clock())
            put("cmd", cmd)
            put("url", url)
        }.toString()

        val req = Request.Builder()
            .url("$base/$topic")
            .header("Title", "Copilot")
            .post(payload.toRequestBody(json))
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw NtfyPublishException("ntfy returned HTTP ${resp.code}")
            }
        }
    }

    companion object {
        private const val SCHEMA_VERSION = 2

        fun ytMusicUrl(form: Form, id: String): String = when (form) {
            Form.PLAYLIST -> "https://music.youtube.com/watch?list=$id&shuffle=1"
            Form.SONG -> "https://music.youtube.com/watch?v=$id"
            Form.DESTINATION -> throw IllegalArgumentException(
                "DESTINATION is not a YouTube Music form; use publishWaze",
            )
        }
    }
}
