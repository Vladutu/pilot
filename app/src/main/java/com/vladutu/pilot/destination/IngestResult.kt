package com.vladutu.pilot.destination

/**
 * Outcome of [DestinationPipeline.ingest]. Carries the exact toast string so callers
 * (ShareReceiverActivity, AddDestinationDialog) don't duplicate copy.
 */
sealed interface IngestResult {
    val toastText: String

    data class Success(val title: String) : IngestResult {
        override val toastText: String = "Sent: $title"
    }

    /** The URL isn't recognized as Maps or Waze. */
    data object NotARecognizedLink : IngestResult {
        override val toastText: String = "Not a Maps or Waze link"
    }

    /** A Waze URL was provided, but its host isn't on the allowlist. */
    data object UnsupportedWazeHost : IngestResult {
        override val toastText: String = "Unsupported Waze link"
    }

    /** waze.papko.org returned a non-302 (HTML error page). */
    data object ConversionFailed : IngestResult {
        override val toastText: String = "Couldn't convert link"
    }

    /** Network failure during conversion. */
    data object NoConnection : IngestResult {
        override val toastText: String = "No connection"
    }

    /** Conversion succeeded; entry was saved; publish failed. Saved entry remains. */
    data class PublishFailed(val title: String) : IngestResult {
        override val toastText: String = "Send failed"
    }

    /** Catalog save failed; publish still succeeded — the navigation went out but the tile won't appear. */
    data class SaveFailed(val title: String) : IngestResult {
        override val toastText: String = "Save failed"
    }

    /** Catalog save AND publish both failed. */
    data class SaveAndPublishFailed(val title: String) : IngestResult {
        override val toastText: String = "Save and send failed"
    }
}
