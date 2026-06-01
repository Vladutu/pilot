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
}
