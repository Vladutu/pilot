package com.vladutu.pilot.settings

/**
 * Single source of truth for the pairing-topic format and the `pilot://pair?topic=...`
 * QR/paste payload. Pure string logic only — no `android.net.Uri` — so it runs on JVM
 * unit tests and is identical across every entry point (paste, QR, deep link).
 */
object TopicPairing {

    const val SCHEME = "pilot"
    const val HOST = "pair"
    const val TOPIC_PARAM = "topic"

    private val TOPIC_REGEX = Regex("^copilot-[0-9a-f]{32}$")

    /** Returns the trimmed topic if it matches the shared format, else null. */
    fun validate(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        return if (TOPIC_REGEX.matches(t)) t else null
    }

    /**
     * If [raw] is a `pilot://pair?topic=<topic>` URI, extracts the `topic` query
     * parameter and validates it. Returns null for any other input (wrong scheme/host,
     * missing or invalid topic, or a non-URI string).
     */
    fun parsePairUri(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        val prefix = "$SCHEME://$HOST?"
        if (!s.startsWith(prefix)) return null
        val query = s.substring(prefix.length)
        val topic = query
            .split('&')
            .firstOrNull { it.substringBefore('=') == TOPIC_PARAM }
            ?.substringAfter('=', "")
        return validate(topic)
    }
}
