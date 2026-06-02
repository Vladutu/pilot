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

    open suspend fun publishYtMusic(form: Form, id: String, title: String?, imageUrl: String?) {
        require(form == Form.PLAYLIST || form == Form.SONG) {
            "publishYtMusic only accepts PLAYLIST or SONG, got $form"
        }
        postEnvelope(
            cmd = "ytmusic",
            form = form,
            url = ytMusicUrl(form, id),
            title = title,
            imageUrl = imageUrl,
        )
    }

    open suspend fun publishWaze(url: String, title: String?) {
        postEnvelope(
            cmd = "waze",
            form = Form.DESTINATION,
            url = url,
            title = title,
            imageUrl = null,
        )
    }

    private suspend fun postEnvelope(
        cmd: String,
        form: Form,
        url: String,
        title: String?,
        imageUrl: String?,
    ) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("v", SCHEMA_VERSION)
            put("ts", clock())
            put("cmd", cmd)
            put("form", form.wire)
            put("url", url)
            title?.let { put("title", it) }
            imageUrl?.let { put("imageUrl", it) }
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
        private const val SCHEMA_VERSION = 3

        fun ytMusicUrl(form: Form, id: String): String = when (form) {
            Form.PLAYLIST -> "https://music.youtube.com/watch?list=$id&shuffle=1"
            Form.SONG -> "https://music.youtube.com/watch?v=$id"
            Form.DESTINATION -> throw IllegalArgumentException(
                "DESTINATION is not a YouTube Music form; use publishWaze",
            )
        }
    }
}
