package com.vladutu.pilot.share

import com.vladutu.pilot.diagnostics.DiagnosticLog
import com.vladutu.pilot.diagnostics.ResolverStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Primary Maps → Waze resolver that runs entirely on-device: it GET-follows the Google Maps link
 * (short or long) and pulls coordinates out of the resolved URL with [MapsCoordinateExtractor],
 * then builds the Waze deep link.
 *
 * This replaces the round-trip to `waze.papko.org` for the common case. Running on the phone
 * (residential/mobile IP + a real browser User-Agent + GET) gets a clean coordinate URL out of
 * Google far more reliably than papko's server does (datacenter IP + `python-requests` UA + HEAD) —
 * which is exactly why papko intermittently answers `200` (its "Oops" page) instead of the `302`
 * the client expects.
 *
 * Returns null on ANY miss — no coordinates in the resolved URL (e.g. an opaque place-id link that
 * only the Places API can resolve) or a network failure — so the caller falls back to
 * [MapsToWazeConverter]. Never throws.
 */
class InAppMapsToWazeResolver(
    private val client: OkHttpClient,
    private val callTimeoutSec: Long = 10L,
) : MapsResolver {

    override suspend fun resolve(googleMapsUrl: String): MapsResolution? = withContext(Dispatchers.IO) {
        // Fast path: a shared long /maps URL may already carry coordinates — no network needed.
        MapsCoordinateExtractor.extract(googleMapsUrl)?.let { coords ->
            DiagnosticLog.i(TAG, "resolved from shared url without network")
            ResolverStats.record(ResolverStats.Outcome.IN_APP_FAST)
            return@withContext MapsResolution(buildWazeUrl(coords), googleMapsUrl)
        }

        val resolvedUrl = try {
            followToFinalUrl(googleMapsUrl)
        } catch (e: IOException) {
            DiagnosticLog.w(TAG, "in-app resolve failed (${e.javaClass.simpleName}): ${e.message}")
            ResolverStats.record(ResolverStats.Outcome.FALLBACK_NETWORK)
            return@withContext null
        } catch (e: IllegalArgumentException) {
            // Request.Builder().url(...) rejects a malformed URL.
            DiagnosticLog.w(TAG, "in-app resolve rejected url: ${e.message}")
            ResolverStats.record(ResolverStats.Outcome.FALLBACK_NETWORK)
            return@withContext null
        }

        val coords = MapsCoordinateExtractor.extract(resolvedUrl)
        if (coords == null) {
            DiagnosticLog.i(TAG, "no coords in resolved url, falling back to converter: $resolvedUrl")
            ResolverStats.record(ResolverStats.Outcome.FALLBACK_NO_COORDS)
            return@withContext null
        }
        DiagnosticLog.i(TAG, "in-app resolved coords from $resolvedUrl")
        ResolverStats.record(ResolverStats.Outcome.IN_APP_NETWORK)
        MapsResolution(buildWazeUrl(coords), resolvedUrl)
    }

    /**
     * Follows redirects (GET, browser UA) and returns the final URL. We read only [Request.url] of
     * the final response — never the body — so the large Maps HTML page is not downloaded.
     */
    private fun followToFinalUrl(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .get()
            .build()
        val perCallClient = client.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .callTimeout(callTimeoutSec, TimeUnit.SECONDS)
            .build()
        return perCallClient.newCall(req).execute().use { resp ->
            resp.request.url.toString()
        }
    }

    private fun buildWazeUrl(coords: MapsCoordinateExtractor.LatLng): String =
        "https://ul.waze.com/ul?ll=${coords.lat}%2C${coords.lng}&navigate=yes"

    private companion object {
        const val TAG = "InAppMapsResolver"
        const val BROWSER_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
