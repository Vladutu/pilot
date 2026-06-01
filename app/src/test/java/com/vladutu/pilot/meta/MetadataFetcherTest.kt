package com.vladutu.pilot.meta

import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.share.ClassifiedShare
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MetadataFetcherTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var fetcher: MetadataFetcher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fetcher = MetadataFetcher(
            client = OkHttpClient(),
            cacheDir = tmp.root,
            ytMusicBase = server.url("").toString().trimEnd('/'),
            oembedBase = server.url("oembed").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `playlist OG scrape returns title and image URL`() = runTest {
        server.enqueue(MockResponse().setBody("""
            <html><head>
              <meta property="og:title" content="Bachata Romantica">
              <meta property="og:image" content="https://example.com/img.jpg">
            </head></html>
        """.trimIndent()))

        val meta = fetcher.fetch(ClassifiedShare.Playlist(id = "PLabc", provisionalTitle = null))

        assertEquals("Bachata Romantica", meta?.title)
        assertEquals("https://example.com/img.jpg", meta?.imageUrl)
    }

    @Test
    fun `playlist scrape with attribute order swapped still works`() = runTest {
        server.enqueue(MockResponse().setBody("""
            <meta content="Title Two" property="og:title">
            <meta content="https://example.com/two.jpg" property="og:image">
        """.trimIndent()))

        val meta = fetcher.fetch(ClassifiedShare.Playlist(id = "PLabc", provisionalTitle = null))

        assertEquals("Title Two", meta?.title)
        assertEquals("https://example.com/two.jpg", meta?.imageUrl)
    }

    @Test
    fun `playlist scrape with no og tags returns null Meta fields`() = runTest {
        server.enqueue(MockResponse().setBody("<html><body>Consent gate</body></html>"))

        val meta = fetcher.fetch(ClassifiedShare.Playlist(id = "PLabc", provisionalTitle = null))

        assertNull(meta?.title)
        assertNull(meta?.imageUrl)
    }

    @Test
    fun `playlist scrape on 404 returns null Meta`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val meta = fetcher.fetch(ClassifiedShare.Playlist(id = "PLabc", provisionalTitle = null))
        assertNull(meta)
    }

    @Test
    fun `song uses oEmbed first when available`() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"title":"Never Gonna Give You Up","thumbnail_url":"https://example.com/thumb.jpg","author_name":"Rick"}
        """.trimIndent()))

        val meta = fetcher.fetch(ClassifiedShare.Song(id = "dQw4w9WgXcQ", provisionalTitle = null))

        assertEquals("Never Gonna Give You Up", meta?.title)
        assertEquals("https://example.com/thumb.jpg", meta?.imageUrl)
    }

    @Test
    fun `song oEmbed 404 falls back to OG scrape`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))                  // oEmbed
        server.enqueue(MockResponse().setBody("""
            <meta property="og:title" content="Fallback Title">
            <meta property="og:image" content="https://example.com/fb.jpg">
        """.trimIndent()))                                                    // OG scrape

        val meta = fetcher.fetch(ClassifiedShare.Song(id = "dQw4w9WgXcQ", provisionalTitle = null))

        assertEquals("Fallback Title", meta?.title)
        assertEquals("https://example.com/fb.jpg", meta?.imageUrl)
    }

    @Test
    fun `playlist scrape request carries CONSENT cookie and Chrome UA`() = runTest {
        server.enqueue(MockResponse().setBody(""))
        fetcher.fetch(ClassifiedShare.Playlist(id = "PLabc", provisionalTitle = null))
        val req = server.takeRequest()
        assertTrue("Cookie missing CONSENT=YES+", (req.getHeader("Cookie") ?: "").contains("CONSENT=YES+"))
        assertTrue(
            "User-Agent doesn't look like Chrome: ${req.getHeader("User-Agent")}",
            (req.getHeader("User-Agent") ?: "").contains("Chrome")
        )
        assertEquals("en-US", req.getHeader("Accept-Language"))
    }

    @Test
    fun `downloadImage writes file atomically to cacheDir`() = runTest {
        val payload = ByteArray(1024) { it.toByte() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))

        val file = fetcher.downloadImage(
            url = server.url("/img.jpg").toString(),
            form = Form.PLAYLIST,
            id = "PLabc",
        )

        assertNotNull(file)
        assertTrue(file!!.exists())
        assertEquals(payload.size.toLong(), file.length())
        assertTrue(file.name == "playlist-PLabc.jpg")
        // No stray .tmp left behind
        val tmpFiles = file.parentFile!!.listFiles { _, n -> n.endsWith(".tmp") }
        assertEquals(0, tmpFiles?.size ?: 0)
    }
}
