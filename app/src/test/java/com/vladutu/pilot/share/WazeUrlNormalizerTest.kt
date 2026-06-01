package com.vladutu.pilot.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WazeUrlNormalizerTest {

    @Test
    fun normalize_appendsNavigateYes_whenMissing() {
        val result = WazeUrlNormalizer.normalize("https://ul.waze.com/ul?ll=52.5,13.4")
        assertEquals("https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes", result)
    }

    @Test
    fun normalize_returnsVerbatim_whenNavigateYesAlreadyPresent() {
        val url = "https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes"
        assertEquals(url, WazeUrlNormalizer.normalize(url))
    }

    @Test
    fun normalize_returnsVerbatim_whenNavigateYesPresentAmongOtherParams() {
        val url = "https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes&z=10"
        assertEquals(url, WazeUrlNormalizer.normalize(url))
    }

    @Test
    fun normalize_appendsNavigateYes_whenUrlHasNoQuery() {
        val result = WazeUrlNormalizer.normalize("https://waze.com/ul")
        assertEquals("https://waze.com/ul?navigate=yes", result)
    }

    @Test
    fun normalize_acceptsWazeComHost() {
        val result = WazeUrlNormalizer.normalize("https://waze.com/ul?ll=1,2")
        assertTrue(result.startsWith("https://waze.com/"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalize_throws_forNonWazeHost() {
        WazeUrlNormalizer.normalize("https://example.com/ul?ll=1,2")
    }

    @Test(expected = IllegalArgumentException::class)
    fun normalize_throws_forHttpScheme() {
        WazeUrlNormalizer.normalize("http://ul.waze.com/ul?ll=1,2")
    }
}
