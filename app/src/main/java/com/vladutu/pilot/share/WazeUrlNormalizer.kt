package com.vladutu.pilot.share

/**
 * Validates a Waze deep-link URL and ensures it carries `navigate=yes` so Waze
 * starts navigation rather than just showing the pin.
 *
 * The host allowlist mirrors the one Copilot enforces on the receiving side.
 * Validation runs client-side too so the user gets a fast toast instead of a
 * silent rejection from Copilot.
 */
object WazeUrlNormalizer {

    private val ALLOWED_PREFIXES = listOf(
        "https://ul.waze.com/",
        "https://waze.com/",
        "https://www.waze.com/",
    )

    fun normalize(url: String): String {
        require(ALLOWED_PREFIXES.any { url.startsWith(it) }) {
            "URL is not on the Waze host allowlist: $url"
        }
        if (containsNavigateYes(url)) return url

        val separator = if (url.contains('?')) '&' else '?'
        return "$url${separator}navigate=yes"
    }

    private fun containsNavigateYes(url: String): Boolean {
        val queryIdx = url.indexOf('?').takeIf { it >= 0 } ?: return false
        val query = url.substring(queryIdx + 1)
        return query.split('&').any { it == "navigate=yes" }
    }
}
