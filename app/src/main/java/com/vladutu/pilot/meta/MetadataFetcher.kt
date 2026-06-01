package com.vladutu.pilot.meta

import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.share.ClassifiedShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class Meta(val title: String?, val imageUrl: String?)

class MetadataFetcher(
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val ytMusicBase: String = "https://music.youtube.com",
    private val oembedBase: String = "https://www.youtube.com/oembed",
) {
    suspend fun fetch(share: ClassifiedShare.YtMusic): Meta? = withContext(Dispatchers.IO) {
        when (share) {
            is ClassifiedShare.Song -> fetchSong(share.id)
            is ClassifiedShare.Playlist -> scrapeOg("$ytMusicBase/playlist?list=${share.id}")
        }
    }

    suspend fun downloadImage(url: String, form: Form, id: String): File? = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "artwork").apply { mkdirs() }
        val target = File(dir, "${form.wire}-$id.jpg")
        val tmp = File(dir, "${form.wire}-$id.jpg.tmp")
        try {
            val resp = client.newCall(Request.Builder().url(url).build()).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@withContext null
                tmp.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return@withContext null
            }
            target
        } catch (e: Exception) {
            tmp.delete()
            null
        }
    }

    /** Convenience: fetch metadata, download image, write the result to [store]. */
    suspend fun refresh(share: ClassifiedShare.YtMusic, store: CatalogStore) {
        val meta = fetch(share) ?: return
        val title = meta.title?.takeIf { it.isNotBlank() }
        val imageFile = meta.imageUrl?.let { downloadImage(it, share.form, share.id) }
        if (title == null && imageFile == null) return
        store.updateMeta(
            form = share.form,
            id = share.id,
            title = title ?: share.provisionalTitle ?: "Untitled ${share.id}",
            imagePath = imageFile?.absolutePath,
        )
    }

    private fun fetchSong(videoId: String): Meta? {
        val oembedUrl = "$oembedBase?url=$ytMusicBase/watch?v=$videoId&format=json"
        val oembed = runCatching {
            val resp = client.newCall(Request.Builder().url(oembedUrl).build()).execute()
            resp.use { r ->
                if (!r.isSuccessful) return@runCatching null
                val body = r.body?.string() ?: return@runCatching null
                val json = JSONObject(body)
                Meta(
                    title = json.optString("title").takeIf { it.isNotBlank() },
                    imageUrl = json.optString("thumbnail_url").takeIf { it.isNotBlank() },
                )
            }
        }.getOrNull()
        if (oembed?.title != null || oembed?.imageUrl != null) return oembed
        return scrapeOg("$ytMusicBase/watch?v=$videoId")
    }

    private fun scrapeOg(url: String): Meta? {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", CHROME_UA)
            .header("Cookie", "CONSENT=YES+")
            .header("Accept-Language", "en-US")
            .build()
        return try {
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return null
                val html = r.body?.string() ?: return Meta(null, null)
                Meta(
                    title = extractOg(html, "og:title"),
                    imageUrl = extractOg(html, "og:image"),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractOg(html: String, property: String): String? {
        // Match <meta ...> tags in either attribute order.
        val tagRegex = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
        val attrRegex = Regex("""(\w+)\s*=\s*"([^"]*)"""")
        for (tag in tagRegex.findAll(html)) {
            val attrs = attrRegex.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val key = attrs["property"] ?: attrs["name"]
            if (key == property) return attrs["content"]?.let(::htmlDecode)?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun htmlDecode(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")

    private companion object {
        const val CHROME_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
