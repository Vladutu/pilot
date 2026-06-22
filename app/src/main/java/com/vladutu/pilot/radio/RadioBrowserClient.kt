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
 * Searches radio-browser for stations in a given country. Resolves a healthy mirror via
 * [resolver], then GETs /json/stations/search. Returns mapped [RadioStation]s with
 * a non-blank stream URL (radio-browser's `hidebroken=true` already drops dead ones).
 *
 * Also exposes the list of countries radio-browser knows about (for the picker in
 * Settings), cached in memory for the process lifetime since it rarely changes.
 */
class RadioBrowserClient(
    private val client: OkHttpClient,
    private val resolver: RadioBrowserServerResolver,
) {
    @Volatile
    private var cachedCountries: List<RadioCountry>? = null

    suspend fun search(countryCode: String, query: String?): List<RadioStation> = withContext(Dispatchers.IO) {
        val base = resolver.resolve()
            ?: throw RadioBrowserException("no healthy radio-browser server")
        val url = searchUrl(base, countryCode, query)
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

    /**
     * Fetch the countries radio-browser has stations for, for the Settings picker. The
     * result is memoised after the first successful call — the list changes rarely and the
     * picker can be reopened often. Sorted by station count (most stations first).
     */
    suspend fun fetchCountries(): List<RadioCountry> = withContext(Dispatchers.IO) {
        cachedCountries?.let { return@withContext it }
        val base = resolver.resolve()
            ?: throw RadioBrowserException("no healthy radio-browser server")
        val url = countriesUrl(base)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", RadioBrowserServerResolver.USER_AGENT)
            .build()
        DiagnosticLog.i(TAG, "radio countries url=$url")
        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw RadioBrowserException("countries HTTP ${resp.code}")
                val body = resp.body?.string() ?: return@use emptyList()
                RadioCountry.listFrom(body).also { cachedCountries = it }
            }
        } catch (e: RadioBrowserException) {
            throw e
        } catch (e: Exception) {
            throw RadioBrowserException("countries failed: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "RadioClient"

        /**
         * Build the station-search URL for [countryCode] (ISO 3166-1 alpha-2). When [query]
         * is non-blank, add a `name=` filter so the user can narrow results; otherwise return
         * the top 50 by votes.
         */
        fun searchUrl(base: String, countryCode: String, query: String?): String {
            val builder = "$base/json/stations/search".toHttpUrl().newBuilder()
                .addQueryParameter("countrycode", countryCode)
                .addQueryParameter("order", "votes")
                .addQueryParameter("reverse", "true")
                .addQueryParameter("limit", "50")
                .addQueryParameter("hidebroken", "true")
            query?.trim()?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("name", it) }
            return builder.build().toString()
        }

        /** Build the `/json/countries` URL, hiding countries whose stations are all broken. */
        fun countriesUrl(base: String): String =
            "$base/json/countries".toHttpUrl().newBuilder()
                .addQueryParameter("hidebroken", "true")
                .build()
                .toString()
    }
}
