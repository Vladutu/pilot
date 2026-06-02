package com.vladutu.pilot.destination

import android.util.Log
import com.vladutu.pilot.catalog.CatalogEntry
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.net.NtfyPublishException
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.share.ClassifiedShare
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
    private val catalogStore: CatalogStore,
    private val publisher: NtfyPublisher,
    private val metadataFetcher: MetadataFetcher? = null,
    private val backgroundScope: CoroutineScope? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    suspend fun ingest(
        urlText: String,
        manualTitle: String?,
        subject: String?,
    ): IngestResult {
        val classified = UrlClassifier.classifyUrl(urlText, subject)
            ?: return IngestResult.NotARecognizedLink

        return when (classified) {
            is ClassifiedShare.YtMusic -> ingestYtMusic(classified, manualTitle)
            is ClassifiedShare.MapsShare,
            is ClassifiedShare.WazeShare -> ingestDestination(classified, manualTitle)
        }
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
            Log.w(TAG, "metadata fetch failed", e)
            null
        }
        val resolvedTitle = meta?.title?.takeIf { it.isNotBlank() } ?: provisionalTitle
        val resolvedImageUrl = meta?.imageUrl?.takeIf { it.isNotBlank() }

        val imageFile = if (resolvedImageUrl != null && metadataFetcher != null) {
            try { metadataFetcher.downloadImage(resolvedImageUrl, share.form, share.id) }
            catch (e: Exception) { Log.w(TAG, "image download failed", e); null }
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
            true
        } catch (e: Exception) {
            Log.w(TAG, "catalog save failed", e)
            false
        }

        val publishResult = try {
            publisher.publishYtMusic(share.form, share.id, title = resolvedTitle, imageUrl = resolvedImageUrl)
            true
        } catch (e: NtfyPublishException) {
            Log.w(TAG, "publish failed", e)
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
        val wazeUrl: String = when (classified) {
            is ClassifiedShare.MapsShare -> {
                try {
                    converter.convert(classified.rawUrl)
                } catch (e: WazeConversionException) {
                    Log.w(TAG, "conversion failed", e)
                    return classifyConversionError(e)
                }
            }
            is ClassifiedShare.WazeShare -> {
                try {
                    WazeUrlNormalizer.normalize(classified.url)
                } catch (e: IllegalArgumentException) {
                    return IngestResult.UnsupportedWazeHost
                }
            }
            is ClassifiedShare.YtMusic -> error("unreachable")
        }

        val title = resolveTitle(
            manualTitle = manualTitle,
            shareProvisionalTitle = classified.provisionalTitle,
            rawUrl = (classified as? ClassifiedShare.MapsShare)?.rawUrl,
            wazeUrl = wazeUrl,
        )

        val entry = CatalogEntry(
            form = Form.DESTINATION,
            id = wazeUrl,
            title = title,
            imagePath = null,
            savedAt = clock(),
        )
        val saveOk = try {
            catalogStore.upsert(entry)
            true
        } catch (e: Exception) {
            Log.w(TAG, "catalog save failed", e)
            false
        }

        return try {
            publisher.publishWaze(wazeUrl, title = title)
            if (saveOk) IngestResult.Success(title) else IngestResult.SaveFailed(title)
        } catch (e: NtfyPublishException) {
            Log.w(TAG, "publish failed", e)
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
