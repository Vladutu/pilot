package com.vladutu.pilot.share

import com.vladutu.pilot.catalog.Form

sealed interface ClassifiedShare {
    val provisionalTitle: String?

    /** Existing YT Music path; downstream consumers branch on this marker. */
    sealed interface YtMusic : ClassifiedShare {
        val id: String
        val form: Form
    }

    data class Playlist(
        override val id: String,
        override val provisionalTitle: String?,
    ) : YtMusic {
        override val form: Form get() = Form.PLAYLIST
    }

    data class Song(
        override val id: String,
        override val provisionalTitle: String?,
    ) : YtMusic {
        override val form: Form get() = Form.SONG
    }

    /** A Google Maps URL that still needs conversion before publish. */
    data class MapsShare(
        val rawUrl: String,
        override val provisionalTitle: String?,
    ) : ClassifiedShare

    /** A Waze URL ready for publish (after normalization). */
    data class WazeShare(
        val url: String,
        override val provisionalTitle: String?,
    ) : ClassifiedShare
}
