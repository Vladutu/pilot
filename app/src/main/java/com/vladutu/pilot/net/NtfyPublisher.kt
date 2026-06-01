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

class NtfyPublisher(
    private val client: OkHttpClient,
    private val base: String,
    private val topic: String,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val json = "application/json".toMediaType()

    suspend fun publishYtMusic(form: Form, id: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("v", 1)
            put("ts", clock())
            put("cmd", "ytmusic")
            put("form", form.wire)
            put("id", id)
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

    suspend fun publishWaze(url: String) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("v", 1)
            put("ts", clock())
            put("cmd", "waze")
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
}
