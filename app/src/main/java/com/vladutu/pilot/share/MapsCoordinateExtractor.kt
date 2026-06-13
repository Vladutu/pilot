package com.vladutu.pilot.share

import java.util.Locale

/**
 * Extracts WGS84 coordinates from a (resolved) Google Maps URL.
 *
 * This mirrors the regex strategies of the `waze.papko.org` converter
 * (https://github.com/papko26/google-link-to-waze) — **minus** its Google Places API path,
 * which resolves an opaque place-id (`cid`) to coordinates and requires an API key we don't ship.
 * Links whose resolved URL carries no coordinates at all (just a place-id) return null here and are
 * handled by falling back to the remote converter.
 *
 * Strategy order deliberately picks the *destination*, not the map viewport:
 *  1. `!3d<lat>!4d<lng>`     — the data-block entity coordinates in `/maps/place/.../data=...` URLs.
 *                              papko's comma-based regex misses these (no comma between the numbers),
 *                              which is exactly why it has to fall back to the Places API for place
 *                              links; reading them directly lets us resolve those links on-device.
 *  2. decimal `lat,lng`      — `/maps/search/<lat>,<lng>` and the `@<lat>,<lng>` camera center.
 *  3. DMS `D°M'S"N D°M'S"E`   — the `%C2%B0`-encoded degree/minute/second form.
 *
 * Coordinates are emitted as canonical decimal strings (locale-independent), already URL-safe.
 */
object MapsCoordinateExtractor {

    /** Decimal-degree coordinates as URL-ready strings, e.g. lat="44.116698", lng="24.186381". */
    data class LatLng(val lat: String, val lng: String)

    // !3d<lat>!4d<lng>  — the authoritative entity coordinates in Maps "data" blocks.
    private val DATA_BLOCK = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")

    // A decimal "lat,lng" pair. Tolerates a URL-encoded '+' (space) after the comma and an explicit
    // sign on either number, matching papko's `([-+]?\d+\.\d+),\s*([-+]?\d+\.\d+)` behavior on
    // strings like "44.116698,+24.186381".
    private val DECIMAL_PAIR = Regex("""([-+]?\d+\.\d+),\+?([-+]?\d+\.\d+)""")

    // DMS form, e.g. "40%C2%B044'54.3%22N+73%C2%B059'08.4%22W".
    // %C2%B0 = '°', '\'' or %27 = minutes, '"' or %22 = seconds.
    private val DMS = Regex(
        """(\d+)%C2%B0(\d+)(?:'|%27)(\d+(?:\.\d+)?)(?:"|%22)([NS])""" +
            """\+?(\d+)%C2%B0(\d+)(?:'|%27)(\d+(?:\.\d+)?)(?:"|%22)([EW])"""
    )

    fun extract(url: String): LatLng? =
        fromDataBlock(url) ?: fromDecimalPair(url) ?: fromDms(url)

    private fun fromDataBlock(url: String): LatLng? {
        val m = DATA_BLOCK.find(url) ?: return null
        return validated(m.groupValues[1], m.groupValues[2])
    }

    private fun fromDecimalPair(url: String): LatLng? {
        val m = DECIMAL_PAIR.find(url) ?: return null
        return validated(m.groupValues[1], m.groupValues[2])
    }

    private fun fromDms(url: String): LatLng? {
        val m = DMS.find(url) ?: return null
        val g = m.groupValues
        val lat = dmsToDecimal(g[1], g[2], g[3], g[4] == "S")
        val lng = dmsToDecimal(g[5], g[6], g[7], g[8] == "W")
        return validated(format6(lat), format6(lng))
    }

    private fun dmsToDecimal(deg: String, min: String, sec: String, negative: Boolean): Double {
        val value = deg.toDouble() + min.toDouble() / 60.0 + sec.toDouble() / 3600.0
        return if (negative) -value else value
    }

    /** Strips a leading '+', verifies both values parse and sit in valid lat/lng ranges. */
    private fun validated(lat: String, lng: String): LatLng? {
        val cleanLat = lat.removePrefix("+")
        val cleanLng = lng.removePrefix("+")
        val la = cleanLat.toDoubleOrNull() ?: return null
        val ln = cleanLng.toDoubleOrNull() ?: return null
        if (la !in -90.0..90.0 || ln !in -180.0..180.0) return null
        return LatLng(cleanLat, cleanLng)
    }

    private fun format6(v: Double): String = String.format(Locale.US, "%.6f", v)
}
