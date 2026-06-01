package com.vladutu.pilot.share

import android.content.Intent

object UrlClassifier {

    private val URL_REGEX = Regex("""https?://\S+""")
    private val YT_HOSTS = setOf("music.youtube.com", "www.youtube.com", "youtube.com", "m.youtube.com", "youtu.be")
    private val MAPS_HOSTS = setOf("maps.google.com", "www.google.com", "google.com", "goo.gl", "maps.app.goo.gl")
    private val WAZE_HOSTS = setOf("ul.waze.com", "waze.com", "www.waze.com")

    fun classifyUrl(text: String, subject: String?): ClassifiedShare? {
        val match = URL_REGEX.find(text) ?: return null
        val urlString = match.value
        val parsed = runCatching { java.net.URI(urlString) }.getOrNull() ?: return null
        val host = parsed.host ?: return null

        val path = parsed.path ?: ""
        val provisionalTitle = subject?.takeIf { it.isNotBlank() }?.trim()
            ?: text.replace(urlString, "").trim().takeIf { it.isNotBlank() }

        // Waze: pasted/shared Waze deep link
        if (host in WAZE_HOSTS) {
            return ClassifiedShare.WazeShare(url = urlString, provisionalTitle = provisionalTitle)
        }

        // Maps: any Google Maps URL (long, short, or the dedicated maps.app.goo.gl)
        if (host == "maps.app.goo.gl" || host == "maps.google.com") {
            return ClassifiedShare.MapsShare(rawUrl = urlString, provisionalTitle = provisionalTitle)
        }
        if (host in setOf("google.com", "www.google.com") && path.startsWith("/maps")) {
            return ClassifiedShare.MapsShare(rawUrl = urlString, provisionalTitle = provisionalTitle)
        }
        if (host == "goo.gl" && path.startsWith("/maps")) {
            return ClassifiedShare.MapsShare(rawUrl = urlString, provisionalTitle = provisionalTitle)
        }

        // YT Music: existing flow, unchanged.
        if (host !in YT_HOSTS) return null
        val query = (parsed.rawQuery ?: "").parseQuery()

        return when {
            host == "youtu.be" -> {
                val id = path.trimStart('/').substringBefore('/').takeIf { it.isNotBlank() } ?: return null
                ClassifiedShare.Song(id = id, provisionalTitle = provisionalTitle)
            }
            path.endsWith("/watch") && query["v"] != null -> {
                ClassifiedShare.Song(id = query.getValue("v"), provisionalTitle = provisionalTitle)
            }
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
