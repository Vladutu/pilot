package com.vladutu.pilot.share

import com.vladutu.pilot.catalog.Form
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SoundCloudResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: SoundCloudResolver

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        resolver = SoundCloudResolver(OkHttpClient())
    }

    @After fun tearDown() = server.shutdown()

    // --- normalize (pure) ---

    @Test
    fun `normalize strips query and keeps track form`() {
        val r = SoundCloudResolver.normalize(
            "https://soundcloud.com/the-real-tibo/la-pola-gola-life?utm_source=clipboard&si=x",
        )
        assertEquals("https://soundcloud.com/the-real-tibo/la-pola-gola-life", r?.canonicalUrl)
        assertEquals(Form.SONG, r?.form)
    }

    @Test
    fun `normalize classifies sets path as playlist`() {
        val r = SoundCloudResolver.normalize(
            "https://soundcloud.com/sc-playlists-eunon/sets/dance-energy?ref=clipboard",
        )
        assertEquals("https://soundcloud.com/sc-playlists-eunon/sets/dance-energy", r?.canonicalUrl)
        assertEquals(Form.PLAYLIST, r?.form)
    }

    @Test
    fun `normalize rewrites m and www hosts`() {
        assertEquals(
            "https://soundcloud.com/a/b",
            SoundCloudResolver.normalize("https://m.soundcloud.com/a/b")?.canonicalUrl,
        )
        assertEquals(
            "https://soundcloud.com/a/b",
            SoundCloudResolver.normalize("https://www.soundcloud.com/a/b")?.canonicalUrl,
        )
    }

    @Test
    fun `normalize rejects non-soundcloud host and empty path`() {
        assertNull(SoundCloudResolver.normalize("https://evil.com/a/b"))
        assertNull(SoundCloudResolver.normalize("https://soundcloud.com/"))
    }

    // --- resolve (network) ---

    @Test
    fun `resolve follows short link redirect and normalizes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(302)
                .setHeader("Location", "https://soundcloud.com/the-real-tibo/la-pola-gola-life?ref=clipboard&si=abc"),
        )
        val r = resolver.resolve(
            server.url("/LgcLHMoSYl1nuCDDai").toString(),
            shortHostOverride = server.hostName,
        )
        assertEquals("https://soundcloud.com/the-real-tibo/la-pola-gola-life", r?.canonicalUrl)
        assertEquals(Form.SONG, r?.form)
    }

    @Test
    fun `resolve returns null on non-redirect response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        assertNull(resolver.resolve(server.url("/xyz").toString(), shortHostOverride = server.hostName))
    }

    @Test
    fun `resolve normalizes canonical url without network`() = runTest {
        // No enqueued response — a network call would hang/fail the test.
        val r = resolver.resolve("https://soundcloud.com/a/sets/b?utm_source=clipboard")
        assertEquals("https://soundcloud.com/a/sets/b", r?.canonicalUrl)
        assertEquals(Form.PLAYLIST, r?.form)
    }

    // --- artworkId (pure) ---

    @Test
    fun `artworkId is filesystem safe`() {
        assertEquals(
            "soundcloud.com_the-real-tibo_la-pola-gola-life",
            SoundCloudResolver.artworkId("https://soundcloud.com/the-real-tibo/la-pola-gola-life"),
        )
    }
}
