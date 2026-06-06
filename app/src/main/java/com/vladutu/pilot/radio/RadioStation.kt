package com.vladutu.pilot.radio

import org.json.JSONArray

/**
 * A station from the radio-browser community DB. [stationUuid] is the stable id
 * (survives stream-URL changes); [streamUrl] is `url_resolved` (the playable stream,
 * preferred over `url`).
 */
data class RadioStation(
    val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val faviconUrl: String?,
    val codec: String?,
    val bitrate: Int,
    val lastCheckOk: Boolean,
) {
    companion object {
        /** Map a radio-browser `/json/stations/search` response body to stations. */
        fun listFrom(body: String): List<RadioStation> {
            val arr = JSONArray(body)
            val out = ArrayList<RadioStation>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val streamUrl = o.optString("url_resolved").takeIf { it.isNotBlank() } ?: continue
                out.add(
                    RadioStation(
                        stationUuid = o.optString("stationuuid"),
                        name = o.optString("name").takeIf { it.isNotBlank() } ?: "Unknown station",
                        streamUrl = streamUrl,
                        faviconUrl = o.optString("favicon").takeIf { it.isNotBlank() },
                        codec = o.optString("codec").takeIf { it.isNotBlank() },
                        bitrate = o.optInt("bitrate", 0),
                        lastCheckOk = o.optInt("lastcheckok", 0) == 1,
                    )
                )
            }
            return out
        }
    }
}
