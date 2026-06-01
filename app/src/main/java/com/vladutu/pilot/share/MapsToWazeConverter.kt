package com.vladutu.pilot.share

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Converts a Google Maps URL into a Waze deep-link URL by POSTing the Maps URL to
 * a third-party converter service ([endpoint], default `https://waze.papko.org/`).
 *
 * The service responds with a `302 Found` whose `Location` header is the Waze URL
 * (`https://ul.waze.com/ul?ll=<lat>,<lng>&navigate=yes`). Any other shape — 200 HTML
 * error page, missing Location, network failure — surfaces as [WazeConversionException].
 *
 * Redirects MUST NOT be followed: following the 302 would land on `ul.waze.com` and
 * return its marketing HTML, masking the answer we wanted from the header.
 */
class MapsToWazeConverter(
    private val client: OkHttpClient,
    private val endpoint: String,
    private val callTimeoutSec: Long = 10L,
) {

    suspend fun convert(googleMapsUrl: String): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("url", googleMapsUrl).build()
        val req = Request.Builder().url(endpoint).post(body).build()

        val perCallClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(callTimeoutSec, TimeUnit.SECONDS)
            .build()

        try {
            perCallClient.newCall(req).execute().use { resp ->
                if (resp.code != 302) {
                    throw WazeConversionException("converter returned HTTP ${resp.code} (expected 302)")
                }
                val location = resp.header("Location")
                    ?: throw WazeConversionException("302 response had no Location header")
                location
            }
        } catch (e: IOException) {
            if (e is WazeConversionException) throw e
            throw WazeConversionException("converter request failed: ${e.message}", e)
        }
    }
}
