package com.vladutu.pilot.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class Form {
    PLAYLIST,
    SONG;

    /** The lowercase string used on the ntfy wire (`"playlist"` / `"song"`). */
    val wire: String get() = name.lowercase()
}
