package com.vladutu.pilot.radio

import org.json.JSONArray

/**
 * A country offered by radio-browser's `/json/countries` endpoint. [code] is the
 * ISO 3166-1 alpha-2 code (e.g. "RO") used as the `countrycode` search filter;
 * [name] is the human-readable name shown in the picker; [stationCount] is how many
 * stations radio-browser knows for it (used to sort popular countries first).
 */
data class RadioCountry(
    val code: String,
    val name: String,
    val stationCount: Int = 0,
) {
    companion object {
        /**
         * Map a radio-browser `/json/countries` response body to countries, dropping
         * entries with a blank name or code, sorted by [stationCount] descending so the
         * countries with the most stations surface at the top of the picker.
         */
        fun listFrom(body: String): List<RadioCountry> {
            val arr = JSONArray(body)
            val out = ArrayList<RadioCountry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("iso_3166_1").takeIf { it.isNotBlank() } ?: continue
                val name = o.optString("name").takeIf { it.isNotBlank() } ?: continue
                out.add(RadioCountry(code = code, name = name, stationCount = o.optInt("stationcount", 0)))
            }
            out.sortByDescending { it.stationCount }
            return out
        }
    }
}
