package com.vladutu.pilot.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsCoordinateExtractorTest {

    @Test
    fun extractsDecimalPair_fromSearchUrl() {
        val url = "https://www.google.com/maps/search/44.116698,+24.186381?entry=tts&skid=abc"
        val c = MapsCoordinateExtractor.extract(url)
        assertEquals("44.116698", c?.lat)
        assertEquals("24.186381", c?.lng)
    }

    @Test
    fun extractsNegativeDecimalPair() {
        val url = "https://www.google.com/maps/search/-33.8688,+151.2093"
        val c = MapsCoordinateExtractor.extract(url)
        assertEquals("-33.8688", c?.lat)
        assertEquals("151.2093", c?.lng)
    }

    @Test
    fun prefersDataBlockOverViewportAtCoords() {
        // @ coords are the map camera (viewport); !3d/!4d are the true destination.
        val url = "https://www.google.com/maps/place/Brandenburg+Gate/" +
            "@52.5000000,13.4000000,17z/data=!3m1!4b1!4d13.3777041!3d52.5162746"
        // (order in real URLs is !3d before !4d)
        val url2 = "https://www.google.com/maps/place/Brandenburg+Gate/" +
            "@52.5000000,13.4000000,17z/data=!3m1!4b1!3d52.5162746!4d13.3777041"
        val c = MapsCoordinateExtractor.extract(url2)
        assertEquals("52.5162746", c?.lat)
        assertEquals("13.3777041", c?.lng)
        // The malformed-order variant has no !3d..!4d adjacency, so it falls to the @ decimal pair.
        val c1 = MapsCoordinateExtractor.extract(url)
        assertEquals("52.5000000", c1?.lat)
        assertEquals("13.4000000", c1?.lng)
    }

    @Test
    fun extractsDmsCoordinates() {
        // 40°44'54.3"N 73°59'08.4"W  ->  40.748417, -73.985667
        val url = "https://www.google.com/maps/place/40%C2%B044'54.3%22N+73%C2%B059'08.4%22W/data=x"
        val c = MapsCoordinateExtractor.extract(url)
        assertEquals("40.748417", c?.lat)
        assertEquals("-73.985667", c?.lng)
    }

    @Test
    fun extractsLiteralDmsFromShareSubject() {
        // The form Google Maps puts in EXTRA_SUBJECT (literal °, ', ", space-separated).
        val subject = "44°07'29.5\"N 24°17'13.5\"E"
        val c = MapsCoordinateExtractor.extract(subject)
        assertEquals("44.124861", c?.lat)
        assertEquals("24.287083", c?.lng)
    }

    @Test
    fun skipsOutOfRangePair_andPicksValidLaterMatch() {
        // A noisy body: first decimal pair has lat 675 (invalid) and must be skipped.
        val text = "junk 675.723538965336,23.7817915 then q=44.124869,24.287085 end"
        val c = MapsCoordinateExtractor.extract(text)
        assertEquals("44.124869", c?.lat)
        assertEquals("24.287085", c?.lng)
    }

    @Test
    fun returnsNull_whenNoCoordinates() {
        val url = "https://www.google.com/maps/place/Eiffel+Tower"
        assertNull(MapsCoordinateExtractor.extract(url))
    }

    @Test
    fun returnsNull_whenCoordinatesOutOfRange() {
        val url = "https://www.google.com/maps/search/999.0,+999.0"
        assertNull(MapsCoordinateExtractor.extract(url))
    }
}
