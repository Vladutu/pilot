package com.vladutu.pilot.net

import com.vladutu.pilot.catalog.Form
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NtfyPublisherTest {

    private lateinit var server: MockWebServer
    private lateinit var publisher: NtfyPublisher

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        publisher = NtfyPublisher(
            client = OkHttpClient(),
            base = server.url("").toString().trimEnd('/'),
            topic = "topic",
            clock = { 12345L },
        )
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `publishYtMusic playlist sends v3 envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishYtMusic(Form.PLAYLIST, "OLAK5uy_xxx", title = "Mix", imageUrl = "https://img/x.jpg")
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals(3, body.getInt("v"))
        assertEquals("ytmusic", body.getString("cmd"))
        assertEquals("playlist", body.getString("form"))
        assertEquals("Mix", body.getString("title"))
        assertEquals("https://img/x.jpg", body.getString("imageUrl"))
        assertTrue(body.getString("url").contains("watch?list=OLAK5uy_xxx"))
    }

    @Test fun `publishYtMusic song with null title and imageUrl omits or nulls them`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishYtMusic(Form.SONG, "abc123", title = null, imageUrl = null)
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals("song", body.getString("form"))
        // either absent or explicit null is acceptable; assert v3 didn't include a real string
        assertTrue(!body.has("title") || body.isNull("title"))
        assertTrue(!body.has("imageUrl") || body.isNull("imageUrl"))
    }

    @Test fun `publishWaze sends destination envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishWaze(url = "https://ul.waze.com/ul?ll=1,2", title = "Home")
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals("waze", body.getString("cmd"))
        assertEquals("destination", body.getString("form"))
        assertEquals("Home", body.getString("title"))
        assertFalse(body.has("imageUrl") && !body.isNull("imageUrl"))
    }

    @Test fun `publishMaps sends destination envelope with maps cmd`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        val mapsUrl = "https://www.google.com/maps/place/Brandenburg+Gate/@52.5,13.4,17z/"
        publisher.publishMaps(url = mapsUrl, title = "Brandenburg Gate")
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals(3, body.getInt("v"))
        assertEquals("maps", body.getString("cmd"))
        assertEquals("destination", body.getString("form"))
        assertEquals(mapsUrl, body.getString("url"))
        assertEquals("Brandenburg Gate", body.getString("title"))
        assertFalse(body.has("imageUrl") && !body.isNull("imageUrl"))
    }

    @Test fun `publishRadio sends radio envelope`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishRadio(
            streamUrl = "https://live.example.ro/europafm.mp3",
            title = "Europa FM",
            imageUrl = "https://example.ro/fav.png",
        )
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals(3, body.getInt("v"))
        assertEquals("radio", body.getString("cmd"))
        assertEquals("radio", body.getString("form"))
        assertEquals("https://live.example.ro/europafm.mp3", body.getString("url"))
        assertEquals("Europa FM", body.getString("title"))
        assertEquals("https://example.ro/fav.png", body.getString("imageUrl"))
    }

    @Test fun `publishRadio omits null imageUrl`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishRadio(streamUrl = "https://live.example.ro/x.mp3", title = "X", imageUrl = null)
        val req = server.takeRequest()
        val body = JSONObject(req.body.readUtf8())
        assertEquals("radio", body.getString("cmd"))
        assertTrue(!body.has("imageUrl") || body.isNull("imageUrl"))
    }
}
