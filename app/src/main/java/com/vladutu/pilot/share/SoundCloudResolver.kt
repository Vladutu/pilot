package com.vladutu.pilot.share

import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI

/**
 * @property canonicalUrl `https://soundcloud.com/<artist>/<slug>` (playlists: `/sets/<slug>`), query stripped.
 * @property form         PLAYLIST when the path's second segment is `sets`, else SONG.
 */
data class SoundCloudResolution(
    val canonicalUrl: String,
    val form: Form,
)

/**
 * Resolves a shared SoundCloud URL to canonical form and classifies it.
 * Short links (on.soundcloud.com / snd.sc) take one non-following GET and read
 * the redirect Location; canonical links never touch the network. Never throws:
 * null means "fall back to publishing the raw URL as-is, form=song".
 *
 * [shortHostOverride] lets tests point the short-host check at MockWebServer.
 */
open class SoundCloudResolver(client: OkHttpClient) {

    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    open suspend fun resolve(rawUrl: String, shortHostOverride: String? = null): SoundCloudResolution? =
        withContext(Dispatchers.IO) {
            val host = runCatching { URI(rawUrl).host }.getOrNull() ?: return@withContext null
            val shortHosts = if (shortHostOverride != null) SHORT_HOSTS + shortHostOverride else SHORT_HOSTS
            if (host !in shortHosts) return@withContext normalize(rawUrl)

            val location = try {
                noRedirectClient.newCall(Request.Builder().url(rawUrl).build()).execute().use { r ->
                    if (r.code in 300..399) r.header("Location") else null
                }
            } catch (e: Exception) {
                DiagnosticLog.w(TAG, "short link resolution failed: ${e.javaClass.simpleName}", e)
                null
            } ?: return@withContext null
            normalize(location)
        }

    companion object {
        private const val TAG = "SoundCloud"
        private val SHORT_HOSTS = setOf("on.soundcloud.com", "snd.sc")
        private val CANONICAL_HOSTS = setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com")

        /** Pure canonicalization: force the bare soundcloud.com host, drop query/fragment, classify by /sets/. */
        fun normalize(url: String): SoundCloudResolution? {
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.host !in CANONICAL_HOSTS) return null
            val segments = (uri.path ?: "").split('/').filter { it.isNotBlank() }
            if (segments.isEmpty()) return null
            val form = if (segments.size >= 2 && segments[1] == "sets") Form.PLAYLIST else Form.SONG
            return SoundCloudResolution(
                canonicalUrl = "https://soundcloud.com/" + segments.joinToString("/"),
                form = form,
            )
        }

        /** Filesystem-safe artwork cache id: scheme dropped, slashes flattened. */
        fun artworkId(url: String): String =
            url.substringAfter("://").replace('/', '_')
    }
}
