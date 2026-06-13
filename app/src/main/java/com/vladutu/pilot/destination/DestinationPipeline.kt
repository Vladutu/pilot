package com.vladutu.pilot.destination

import com.vladutu.pilot.catalog.CatalogEntry
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.diagnostics.DiagnosticLog
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.net.NtfyPublishException
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.share.ClassifiedShare
import com.vladutu.pilot.share.MapsResolver
import com.vladutu.pilot.share.MapsToWazeConverter
import com.vladutu.pilot.share.UrlClassifier
import com.vladutu.pilot.share.WazeConversionException
import com.vladutu.pilot.share.WazeUrlNormalizer
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import java.net.URLDecoder

/**
 * Orchestrates the share / manual-entry pipeline for every recognized link:
 *   - YT Music song/playlist → catalog upsert → publishYtMusic → fire-and-forget metadata refresh
 *   - Google Maps URL       → convert → catalog upsert → publishWaze
 *   - Waze URL              → normalize → catalog upsert → publishWaze
 *
 * Used by both [com.vladutu.pilot.share.ShareReceiverActivity] (share intent path) and
 * [com.vladutu.pilot.ui.AddUrlDialog] (manual-entry path).
 *
 * [metadataFetcher] and [backgroundScope] may be null in tests that don't exercise the YT Music
 * branch; production wiring always supplies them.
 */
class DestinationPipeline(
    private val converter: MapsToWazeConverter,
    // Primary, on-device Maps→Waze resolver. Null disables it (the converter is then used directly),
    // which keeps the original behavior for tests that don't wire one. Production always supplies it.
    private val inAppResolver: MapsResolver? = null,
    private val catalogStore: CatalogStore,
    private val publisher: NtfyPublisher,
    private val metadataFetcher: MetadataFetcher? = null,
    private val backgroundScope: CoroutineScope? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
    // Diagnostic: snapshots the process importance / background-network restriction at publish time.
    // Null in tests; production supplies a probe backed by the app Context. See [ProcessState].
    private val processStateProbe: (() -> String)? = null,
) {

    suspend fun ingest(
        urlText: String,
        manualTitle: String?,
        subject: String?,
    ): IngestResult {
        DiagnosticLog.i(TAG, "ingest urlText=$urlText subject=$subject manualTitle=$manualTitle")
        val classified = UrlClassifier.classifyUrl(urlText, subject)
        if (classified == null) {
            DiagnosticLog.w(TAG, "url not recognized: $urlText")
            return IngestResult.NotARecognizedLink
        }
        DiagnosticLog.i(TAG, "classified as ${classified::class.simpleName}")

        val result = when (classified) {
            is ClassifiedShare.YtMusic -> ingestYtMusic(classified, manualTitle)
            is ClassifiedShare.MapsShare,
            is ClassifiedShare.WazeShare -> ingestDestination(classified, manualTitle)
        }
        DiagnosticLog.i(TAG, "ingest result=${result::class.simpleName}")
        return result
    }

    private suspend fun ingestYtMusic(
        share: ClassifiedShare.YtMusic,
        manualTitle: String?,
    ): IngestResult {
        val provisionalTitle = manualTitle?.trim()?.takeIf { it.isNotBlank() }
            ?: share.provisionalTitle?.takeIf { it.isNotBlank() }
            ?: "Untitled ${share.id}"

        // Await metadata — if it fails, fall back to provisional title + null imageUrl.
        val meta = try {
            metadataFetcher?.fetch(share)
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "metadata fetch failed", e)
            null
        }
        val resolvedTitle = meta?.title?.takeIf { it.isNotBlank() } ?: provisionalTitle
        val resolvedImageUrl = meta?.imageUrl?.takeIf { it.isNotBlank() }
        DiagnosticLog.i(TAG, "ytmusic resolved title='$resolvedTitle' imageUrl=$resolvedImageUrl")

        val imageFile = if (resolvedImageUrl != null && metadataFetcher != null) {
            try { metadataFetcher.downloadImage(resolvedImageUrl, share.form, share.id) }
            catch (e: Exception) { DiagnosticLog.w(TAG, "image download failed", e); null }
        } else null

        val entry = CatalogEntry(
            form = share.form,
            id = share.id,
            title = resolvedTitle,
            imagePath = imageFile?.absolutePath,
            imageUrl = resolvedImageUrl,
            savedAt = clock(),
        )
        val saveOk = try {
            catalogStore.upsert(entry)
            DiagnosticLog.i(TAG, "catalog upsert ok ${share.form}:${share.id}")
            true
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "catalog save failed", e)
            false
        }

        processStateProbe?.let { DiagnosticLog.i(TAG, "pre-publish state: ${it()}") }
        val publishResult = try {
            publisher.publishYtMusic(share.form, share.id, title = resolvedTitle, imageUrl = resolvedImageUrl)
            true
        } catch (e: NtfyPublishException) {
            DiagnosticLog.w(TAG, "publish failed (NtfyPublishException)", e)
            false
        }

        return when {
            saveOk && publishResult -> IngestResult.Success(resolvedTitle)
            saveOk && !publishResult -> IngestResult.PublishFailed(resolvedTitle)
            !saveOk && publishResult -> IngestResult.SaveFailed(resolvedTitle)
            else -> IngestResult.SaveAndPublishFailed(resolvedTitle)
        }
    }

    private suspend fun ingestDestination(
        classified: ClassifiedShare,
        manualTitle: String?,
    ): IngestResult {
        // Title source defaults to the raw shared URL; an in-app resolution upgrades it to the
        // resolved URL, which usually carries a /place/<Name>/ segment for a real title.
        var titleSourceUrl: String? = (classified as? ClassifiedShare.MapsShare)?.rawUrl

        val wazeUrl: String = when (classified) {
            is ClassifiedShare.MapsShare -> {
                // In-app resolver is primary: running on-device (residential IP + browser UA + GET)
                // gets a clean coordinate URL out of Google far more reliably than the papko service
                // (datacenter IP + python-requests + HEAD), which is why papko intermittently 200s.
                // Fall back to papko only on an in-app miss (no coords in the resolved URL — e.g. an
                // opaque place-id link needing the Places API — or a network failure).
                // The share subject (carried on MapsShare as provisionalTitle) often holds the
                // destination coords — pass it as a hint so the resolver can skip the network and
                // the fragile goo.gl/maps link resolution entirely.
                val inApp = inAppResolver?.resolve(
                    classified.rawUrl,
                    hints = listOfNotNull(classified.provisionalTitle),
                )
                if (inApp != null) {
                    titleSourceUrl = inApp.resolvedUrl
                    inApp.wazeUrl
                } else {
                    try {
                        converter.convert(classified.rawUrl)
                    } catch (e: WazeConversionException) {
                        DiagnosticLog.w(TAG, "conversion failed", e)
                        return classifyConversionError(e)
                    }
                }
            }
            is ClassifiedShare.WazeShare -> {
                try {
                    WazeUrlNormalizer.normalize(classified.url)
                } catch (e: IllegalArgumentException) {
                    DiagnosticLog.w(TAG, "unsupported waze host: ${classified.url}", e)
                    return IngestResult.UnsupportedWazeHost
                }
            }
            is ClassifiedShare.YtMusic -> error("unreachable")
        }
        DiagnosticLog.i(TAG, "destination wazeUrl=$wazeUrl")

        val googleMapsUrl: String? = (classified as? ClassifiedShare.MapsShare)?.rawUrl

        val title = resolveTitle(
            manualTitle = manualTitle,
            shareProvisionalTitle = classified.provisionalTitle,
            rawUrl = titleSourceUrl,
            wazeUrl = wazeUrl,
        )

        val entry = CatalogEntry(
            form = Form.DESTINATION,
            id = wazeUrl,
            title = title,
            imagePath = null,
            googleMapsUrl = googleMapsUrl,
            savedAt = clock(),
        )
        val saveOk = try {
            catalogStore.upsert(entry)
            DiagnosticLog.i(TAG, "catalog upsert ok DESTINATION:$wazeUrl")
            true
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "catalog save failed", e)
            false
        }

        processStateProbe?.let { DiagnosticLog.i(TAG, "pre-publish state: ${it()}") }
        return try {
            publisher.publishWaze(wazeUrl, title = title)
            if (saveOk) IngestResult.Success(title) else IngestResult.SaveFailed(title)
        } catch (e: NtfyPublishException) {
            DiagnosticLog.w(TAG, "publish failed (NtfyPublishException)", e)
            if (saveOk) IngestResult.PublishFailed(title) else IngestResult.SaveAndPublishFailed(title)
        }
    }

    private fun classifyConversionError(e: WazeConversionException): IngestResult {
        val cause = e.cause
        return if (cause is IOException && cause !is WazeConversionException) {
            IngestResult.NoConnection
        } else {
            IngestResult.ConversionFailed
        }
    }

    /**
     * Destination title resolution chain:
     * 1. manualTitle (dialog)            → highest
     * 2. shareProvisionalTitle           → from EXTRA_SUBJECT or first non-URL text line
     * 3. /place/<name>/ segment in rawUrl
     * 4. "Destination XXXX" where XXXX = last 4 chars of wazeUrl
     */
    private fun resolveTitle(
        manualTitle: String?,
        shareProvisionalTitle: String?,
        rawUrl: String?,
        wazeUrl: String,
    ): String {
        manualTitle?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        shareProvisionalTitle?.takeIf { it.isNotBlank() }?.let { return it }
        rawUrl?.let { extractPlaceName(it) }?.let { return it }
        val suffix = wazeUrl.takeLast(4)
        return "Destination $suffix"
    }

    private fun extractPlaceName(url: String): String? {
        val placeRegex = Regex("""/place/([^/?]+)/?""")
        val match = placeRegex.find(url) ?: return null
        val raw = match.groupValues[1]
        return try {
            URLDecoder.decode(raw.replace('+', ' '), "UTF-8").takeIf { it.isNotBlank() }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val TAG = "DestinationPipeline"
    }
}
