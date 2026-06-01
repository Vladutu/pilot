package com.vladutu.pilot.catalog

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogEntryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trips an entry with imagePath`() {
        val original = CatalogEntry(
            form = Form.PLAYLIST,
            id = "PLabc",
            title = "Bachata",
            imagePath = "/data/data/x/cache/artwork/playlist-PLabc.jpg",
            savedAt = 1717250000L,
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<CatalogEntry>(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `round-trips an entry with null imagePath`() {
        val original = CatalogEntry(
            form = Form.SONG,
            id = "dQw4w9WgXcQ",
            title = "Untitled",
            imagePath = null,
            savedAt = 1717250000L,
        )
        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<CatalogEntry>(encoded)
        assertEquals(original, decoded)
        assertEquals(null, decoded.imagePath)
    }
}
