package com.vladutu.pilot.share

import com.vladutu.pilot.catalog.Form

sealed interface ClassifiedShare {
    val id: String
    val provisionalTitle: String?
    val form: Form

    data class Playlist(
        override val id: String,
        override val provisionalTitle: String?,
    ) : ClassifiedShare {
        override val form: Form get() = Form.PLAYLIST
    }

    data class Song(
        override val id: String,
        override val provisionalTitle: String?,
    ) : ClassifiedShare {
        override val form: Form get() = Form.SONG
    }
}
