package com.vladutu.pilot.share

import android.content.Intent

object UrlClassifier {

    private val URL_REGEX = Regex("""https?://\S+""")
    private val YT_HOSTS = setOf("music.youtube.com", "www.youtube.com", "youtube.com", "m.youtube.com", "youtu.be")

    /**
     * Pure-Kotlin classification entry point. Tests target this directly to avoid
     * needing a real Android Intent.
     */
    fun classifyUrl(text: String, subject: String?): ClassifiedShare? {
        val match = URL_REGEX.find(text) ?: return null
        val urlString = match.value
        val parsed = runCatching { java.net.URI(urlString) }.getOrNull() ?: return null
        val host = parsed.host ?: return null
        if (host !in YT_HOSTS) return null

        val path = parsed.path ?: ""
        val query = (parsed.rawQuery ?: "").parseQuery()

        val provisionalTitle = subject?.takeIf { it.isNotBlank() }?.trim()
            ?: text.replace(urlString, "").trim().takeIf { it.isNotBlank() }

        return when {
            // youtu.be short links: path is the video id with a leading slash.
            host == "youtu.be" -> {
                val id = path.trimStart('/').substringBefore('/').takeIf { it.isNotBlank() } ?: return null
                ClassifiedShare.Song(id = id, provisionalTitle = provisionalTitle)
            }
            // /watch?v=... — when both v and list are present, song wins.
            path.endsWith("/watch") && query["v"] != null -> {
                ClassifiedShare.Song(id = query.getValue("v"), provisionalTitle = provisionalTitle)
            }
            // /playlist?list=...
            path.endsWith("/playlist") && query["list"] != null -> {
                ClassifiedShare.Playlist(id = query.getValue("list"), provisionalTitle = provisionalTitle)
            }
            else -> null
        }
    }

    fun classify(intent: Intent): ClassifiedShare? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        return classifyUrl(text = text, subject = subject)
    }

    private fun String.parseQuery(): Map<String, String> =
        if (isEmpty()) emptyMap()
        else split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null else pair.substring(0, idx) to java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }.toMap()
}
