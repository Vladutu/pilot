package com.vladutu.pilot.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsNavUrlBuilderTest {

    @Test
    fun fromWazeUrl_encodedComma_buildsNavUrl() {
        val url = MapsNavUrlBuilder.fromWazeUrl(
            "https://ul.waze.com/ul?ll=44.2928416%2C23.7912555&navigate=yes"
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=44.2928416,23.7912555&travelmode=driving&dir_action=navigate",
            url,
        )
    }

    @Test
    fun fromWazeUrl_plainComma_buildsNavUrl() {
        val url = MapsNavUrlBuilder.fromWazeUrl("https://ul.waze.com/ul?ll=52.5,13.4")
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=52.5,13.4&travelmode=driving&dir_action=navigate",
            url,
        )
    }

    @Test
    fun fromWazeUrl_llNotFirstParam_stillFound() {
        val url = MapsNavUrlBuilder.fromWazeUrl(
            "https://ul.waze.com/ul?navigate=yes&ll=10.0%2C20.0"
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=10.0,20.0&travelmode=driving&dir_action=navigate",
            url,
        )
    }

    @Test
    fun fromWazeUrl_missingLl_returnsNull() {
        assertNull(MapsNavUrlBuilder.fromWazeUrl("https://ul.waze.com/ul?navigate=yes"))
    }

    @Test
    fun fromWazeUrl_noQuery_returnsNull() {
        assertNull(MapsNavUrlBuilder.fromWazeUrl("https://ul.waze.com/ul"))
    }

    @Test
    fun fromWazeUrl_malformedLl_returnsNull() {
        assertNull(MapsNavUrlBuilder.fromWazeUrl("https://ul.waze.com/ul?ll=abc%2Cdef"))
        assertNull(MapsNavUrlBuilder.fromWazeUrl("https://ul.waze.com/ul?ll=44.0"))
        assertNull(MapsNavUrlBuilder.fromWazeUrl("https://ul.waze.com/ul?ll="))
    }
}
