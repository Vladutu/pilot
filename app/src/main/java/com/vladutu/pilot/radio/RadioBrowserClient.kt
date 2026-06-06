package com.vladutu.pilot.radio

import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class RadioBrowserException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Searches radio-browser for Romanian stations. Resolves a healthy mirror via
 * [resolver], then GETs /json/stations/search. Returns mapped [RadioStation]s with
 * a non-blank stream URL (radio-browser's `hidebroken=true` already drops dead ones).
 */
class RadioBrowserClient(
    private val client: OkHttpClient,
    private val resolver: RadioBrowserServerResolver,
) {
    suspend fun searchRomania(query: String?): List<RadioStation> = withContext(Dispatchers.IO) {
        val base = resolver.resolve()
            ?: throw RadioBrowserException("no healthy radio-browser server")
        val url = searchUrl(base, query)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", RadioBrowserServerResolver.USER_AGENT)
            .build()
        DiagnosticLog.i(TAG, "radio search url=$url")
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw RadioBrowserException("search HTTP ${resp.code}")
                val body = resp.body?.string() ?: return@use emptyList()
                RadioStation.listFrom(body)
            }
        } catch (e: RadioBrowserException) {
            throw e
        } catch (e: Exception) {
            throw RadioBrowserException("search failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "RadioClient"

        /**
         * Build the RO station-search URL (spec §3). When [query] is non-blank, add a
         * `name=` filter so the user can narrow results; otherwise return the top 50 by votes.
         */
        fun searchUrl(base: String, query: String?): String {
            val builder = "$base/json/stations/search".toHttpUrl().newBuilder()
                .addQueryParameter("countrycode", "RO")
                .addQueryParameter("order", "votes")
                .addQueryParameter("reverse", "true")
                .addQueryParameter("limit", "50")
                .addQueryParameter("hidebroken", "true")
            query?.trim()?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("name", it) }
            return builder.build().toString()
        }
    }
}
