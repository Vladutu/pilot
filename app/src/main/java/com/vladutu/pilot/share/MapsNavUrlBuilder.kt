package com.vladutu.pilot.share

/**
 * Builds a Google Maps Universal URL that starts turn-by-turn navigation
 * immediately, given a normalized Waze URL containing `ll=<lat>,<lng>`.
 *
 * Maps's plain share URLs (e.g. `https://maps.app.goo.gl/<short>`) only open
 * the place card; the user still has to tap "Directions" then "Start". The
 * Universal URL API's `dir_action=navigate` parameter is the Maps equivalent
 * of Waze's `navigate=yes` — it kicks the user straight into nav mode.
 *
 * Returns null if the Waze URL doesn't carry recoverable coordinates.
 */
object MapsNavUrlBuilder {

    fun fromWazeUrl(wazeUrl: String): String? {
        val latLng = extractLatLng(wazeUrl) ?: return null
        return "https://www.google.com/maps/dir/?api=1" +
            "&destination=$latLng" +
            "&travelmode=driving" +
            "&dir_action=navigate"
    }

    private fun extractLatLng(url: String): String? {
        val queryIdx = url.indexOf('?').takeIf { it >= 0 } ?: return null
        val query = url.substring(queryIdx + 1)
        for (param in query.split('&')) {
            val eq = param.indexOf('=')
            if (eq <= 0) continue
            if (param.substring(0, eq) != "ll") continue
            val decoded = param.substring(eq + 1).replace("%2C", ",")
            val parts = decoded.split(',')
            if (parts.size != 2) return null
            if (parts.any { it.isEmpty() || it.toDoubleOrNull() == null }) return null
            return decoded
        }
        return null
    }
}
