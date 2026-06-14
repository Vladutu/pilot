package com.vladutu.pilot.share

import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Converts a Google Maps URL into a Waze deep-link URL. A single attempt; [WazeConversionException]
 * on any failure. The retry loop lives in [ShareConversionController], not here.
 */
interface WazeConverter {
    suspend fun convert(googleMapsUrl: String): String
}

/**
 * [WazeConverter] backed by a third-party converter service ([endpoint], default
 * `https://waze.papko.org/`): POSTs the Maps URL and reads the redirect.
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
) : WazeConverter {

    override suspend fun convert(googleMapsUrl: String): String = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("url", googleMapsUrl).build()
        val req = Request.Builder().url(endpoint).post(body).build()

        val perCallClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .callTimeout(callTimeoutSec, TimeUnit.SECONDS)
            .build()

        DiagnosticLog.i(TAG, "converting $googleMapsUrl")
        try {
            perCallClient.newCall(req).execute().use { resp ->
                if (resp.code != 302) {
                    DiagnosticLog.w(TAG, "converter returned HTTP ${resp.code} (expected 302)")
                    throw WazeConversionException("converter returned HTTP ${resp.code} (expected 302)")
                }
                val location = resp.header("Location")
                    ?: run {
                        DiagnosticLog.w(TAG, "302 had no Location header")
                        throw WazeConversionException("302 response had no Location header")
                    }
                DiagnosticLog.i(TAG, "converted to $location")
                location
            }
        } catch (e: IOException) {
            if (e is WazeConversionException) throw e
            DiagnosticLog.e(TAG, "converter request failed (${e.javaClass.simpleName}): ${e.message}", e)
            throw WazeConversionException("converter request failed: ${e.message}", e)
        }
    }

    private companion object {
        const val TAG = "MapsConv"
    }
}
