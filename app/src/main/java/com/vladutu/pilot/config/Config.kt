package com.vladutu.pilot.config

object Config {
    const val NTFY_BASE: String = "https://ntfy.sh"

    /** Maps→Waze converter endpoint. POST `url=<google maps url>` → 302 with Location: <waze url>. */
    const val WAZE_CONVERTER_URL: String = "https://waze.papko.org/"
}
