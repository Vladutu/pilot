package com.vladutu.pilot.share

import android.content.Intent
import com.vladutu.pilot.catalog.Form

object UrlClassifier {

    private val URL_REGEX = Regex("""https?://\S+""")
    private const val YT_MUSIC_HOST = "music.youtube.com"
    private val YOUTUBE_HOSTS = setOf("www.youtube.com", "youtube.com", "m.youtube.com")
    private val MAPS_HOSTS = setOf("maps.google.com", "www.google.com", "google.com", "goo.gl", "maps.app.goo.gl")
    private val WAZE_HOSTS = setOf("ul.waze.com", "waze.com", "www.waze.com")
    private val SOUNDCLOUD_HOSTS =
        setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com", "on.soundcloud.com", "snd.sc")

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

        // SoundCloud: canonical or short link; resolution + song/playlist split happen later
        // in SoundCloudResolver (short links need a network hop; this classifier stays pure).
        if (host in SOUNDCLOUD_HOSTS) {
            return ClassifiedShare.SoundCloudShare(
                rawUrl = urlString,
                provisionalTitle = provisionalTitle?.let(::stripSoundCloudBoilerplate),
            )
        }

        // Plain YouTube (the video app, not YT Music): the YouTube app shares youtu.be
        // short links; browser copies use youtube.com. These open YouTube on the car.
        if (host == "youtu.be") {
            val id = path.trimStart('/').substringBefore('/').takeIf { it.isNotBlank() } ?: return null
            return ClassifiedShare.YouTubeShare(id = id, form = Form.SONG, provisionalTitle = provisionalTitle)
        }
        if (host in YOUTUBE_HOSTS) {
            val query = (parsed.rawQuery ?: "").parseQuery()
            return when {
                path.endsWith("/watch") && query["v"] != null ->
                    ClassifiedShare.YouTubeShare(query.getValue("v"), Form.SONG, provisionalTitle)
                path.endsWith("/playlist") && query["list"] != null ->
                    ClassifiedShare.YouTubeShare(query.getValue("list"), Form.PLAYLIST, provisionalTitle)
                path.startsWith("/shorts/") -> {
                    val id = path.removePrefix("/shorts/").substringBefore('/').takeIf { it.isNotBlank() }
                        ?: return null
                    ClassifiedShare.YouTubeShare(id, Form.SONG, provisionalTitle)
                }
                else -> null
            }
        }

        // YT Music: existing flow, unchanged.
        if (host != YT_MUSIC_HOST) return null
        val query = (parsed.rawQuery ?: "").parseQuery()

        return when {
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

    /** "Listen to X, a playlist by Y on #SoundCloud" → "X by Y". Best-effort, English-only; oEmbed overrides it. */
    private fun stripSoundCloudBoilerplate(title: String): String {
        val cleaned = title.trim()
            .removePrefix("Listen to ")
            .removeSuffix(" on #SoundCloud")
            .replace(", a playlist by ", " by ")
            .trim()
        return cleaned.ifBlank { title }
    }

    private fun String.parseQuery(): Map<String, String> =
        if (isEmpty()) emptyMap()
        else split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null else pair.substring(0, idx) to java.net.URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }.toMap()
}
