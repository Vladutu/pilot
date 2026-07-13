package com.vladutu.pilot.catalog

import kotlinx.serialization.Serializable

@Serializable
data class CatalogEntry(
    val form: Form,
    val id: String,
    val title: String,
    val imagePath: String?,
    val imageUrl: String? = null,
    val googleMapsUrl: String? = null,
    /** For RADIO: the raw stream URL (id holds the stationUuid, not the URL). Null for other forms. */
    val url: String? = null,
    /** Publish routing override: "soundcloud" for SoundCloud entries (id holds the canonical URL),
     *  "youtube" for plain-YouTube entries (id holds the video/playlist id). Null = legacy, derived from form. */
    val cmd: String? = null,
    val savedAt: Long,
)
