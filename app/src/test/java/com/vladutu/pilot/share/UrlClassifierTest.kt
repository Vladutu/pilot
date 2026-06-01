package com.vladutu.pilot.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlClassifierTest {

    @Test
    fun `music_youtube playlist URL classifies as Playlist`() {
        val r = UrlClassifier.classifyUrl(
            text = "https://music.youtube.com/playlist?list=PLabc",
            subject = null,
        )
        assertTrue(r is ClassifiedShare.Playlist)
        assertEquals("PLabc", (r as ClassifiedShare.Playlist).id)
    }

    @Test
    fun `music_youtube watch URL classifies as Song`() {
        val r = UrlClassifier.classifyUrl(
            text = "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            subject = null,
        )
        assertTrue(r is ClassifiedShare.Song)
        assertEquals("dQw4w9WgXcQ", (r as ClassifiedShare.Song).id)
    }

    @Test
    fun `watch URL with both v and list classifies as Song (song wins)`() {
        val r = UrlClassifier.classifyUrl(
            text = "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ",
            subject = null,
        )
        assertTrue(r is ClassifiedShare.Song)
        assertEquals("dQw4w9WgXcQ", (r as ClassifiedShare.Song).id)
    }

    @Test
    fun `youtube_com watch URL also classifies as Song`() {
        val r = UrlClassifier.classifyUrl(
            text = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            subject = null,
        )
        assertTrue(r is ClassifiedShare.Song)
        assertEquals("dQw4w9WgXcQ", (r as ClassifiedShare.Song).id)
    }

    @Test
    fun `m_youtube watch URL classifies as Song`() {
        val r = UrlClassifier.classifyUrl(
            text = "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            subject = null,
        )
        assertTrue(r is ClassifiedShare.Song)
    }

    @Test
    fun `youtu_be short URL classifies as Song`() {
        val r = UrlClassifier.classifyUrl(
            text = "https://youtu.be/dQw4w9WgXcQ",
            subject = null,
        )
        assertTrue(r is ClassifiedShare.Song)
        assertEquals("dQw4w9WgXcQ", (r as ClassifiedShare.Song).id)
    }

    @Test
    fun `youtu_be short URL with query string classifies as Song`() {
        val r = UrlClassifier.classifyUrl(
            text = "https://youtu.be/dQw4w9WgXcQ?si=abcdef",
            subject = null,
        )
        assertTrue(r is ClassifiedShare.Song)
        assertEquals("dQw4w9WgXcQ", (r as ClassifiedShare.Song).id)
    }

    @Test
    fun `text without a URL returns null`() {
        assertNull(UrlClassifier.classifyUrl(text = "Hey check this out", subject = null))
    }

    @Test
    fun `URL on a non-YouTube host returns null`() {
        assertNull(UrlClassifier.classifyUrl(text = "https://example.com/playlist?list=PLabc", subject = null))
    }

    @Test
    fun `empty text returns null`() {
        assertNull(UrlClassifier.classifyUrl(text = "", subject = null))
    }

    @Test
    fun `EXTRA_SUBJECT becomes provisional title when set`() {
        val r = UrlClassifier.classifyUrl(
            text = "https://music.youtube.com/playlist?list=PLabc",
            subject = "Bachata Romantica",
        )
        assertEquals("Bachata Romantica", r!!.provisionalTitle)
    }

    @Test
    fun `EXTRA_TEXT minus the URL becomes provisional title when subject is absent`() {
        val r = UrlClassifier.classifyUrl(
            text = "Bachata Romantica https://music.youtube.com/playlist?list=PLabc",
            subject = null,
        )
        assertEquals("Bachata Romantica", r!!.provisionalTitle)
    }

    @Test
    fun `provisional title is null when neither subject nor non-URL text is available`() {
        val r = UrlClassifier.classifyUrl(
            text = "https://music.youtube.com/playlist?list=PLabc",
            subject = null,
        )
        assertNull(r!!.provisionalTitle)
    }

    @Test
    fun `provisional title trims whitespace`() {
        val r = UrlClassifier.classifyUrl(
            text = "  Bachata Romantica   https://music.youtube.com/playlist?list=PLabc",
            subject = null,
        )
        assertEquals("Bachata Romantica", r!!.provisionalTitle)
    }

    @Test
    fun classifyUrl_returnsMapsShare_forGooglePlaceUrl() {
        val result = UrlClassifier.classifyUrl(
            text = "Brandenburg Gate\nhttps://www.google.com/maps/place/Brandenburg+Gate/@52.5162,13.3777,17z/",
            subject = "Brandenburg Gate",
        )
        assertTrue(result is ClassifiedShare.MapsShare)
        val maps = result as ClassifiedShare.MapsShare
        assertEquals("https://www.google.com/maps/place/Brandenburg+Gate/@52.5162,13.3777,17z/", maps.rawUrl)
        assertEquals("Brandenburg Gate", maps.provisionalTitle)
    }

    @Test
    fun classifyUrl_returnsMapsShare_forShortLink() {
        val result = UrlClassifier.classifyUrl(
            text = "https://maps.app.goo.gl/JN5w7N5BvCcrcVuS9",
            subject = null,
        )
        assertTrue(result is ClassifiedShare.MapsShare)
    }

    @Test
    fun classifyUrl_returnsMapsShare_forGooGlMaps() {
        val result = UrlClassifier.classifyUrl(
            text = "https://goo.gl/maps/abc123",
            subject = null,
        )
        assertTrue(result is ClassifiedShare.MapsShare)
    }

    @Test
    fun classifyUrl_returnsWazeShare_forUlWazeUrl() {
        val result = UrlClassifier.classifyUrl(
            text = "https://ul.waze.com/ul?ll=52.5162%2C13.3777&navigate=yes",
            subject = "Home",
        )
        assertTrue(result is ClassifiedShare.WazeShare)
        val waze = result as ClassifiedShare.WazeShare
        assertEquals("https://ul.waze.com/ul?ll=52.5162%2C13.3777&navigate=yes", waze.url)
        assertEquals("Home", waze.provisionalTitle)
    }

    @Test
    fun classifyUrl_returnsWazeShare_forWazeComUrl() {
        val result = UrlClassifier.classifyUrl(
            text = "https://waze.com/ul?ll=52.5,13.4",
            subject = null,
        )
        assertTrue(result is ClassifiedShare.WazeShare)
    }

    @Test
    fun classifyUrl_returnsNull_forUnknownHost() {
        val result = UrlClassifier.classifyUrl(
            text = "https://example.com/something",
            subject = null,
        )
        assertNull(result)
    }
}
