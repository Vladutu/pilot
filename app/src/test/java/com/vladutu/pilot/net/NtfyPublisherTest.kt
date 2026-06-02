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

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        publisher = NtfyPublisher(
            client = OkHttpClient(),
            base = server.url("").toString().trimEnd('/'),
            topic = "test-topic",
            clock = { 1717250000L },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `publishYtMusic with playlist form posts v2 url with shuffle`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        publisher.publishYtMusic(Form.PLAYLIST, "PLabc123")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/test-topic", recorded.path)
        assertTrue(
            recorded.getHeader("Content-Type")?.startsWith("application/json") == true
        )

        val body = JSONObject(recorded.body.readUtf8())
        assertEquals(2, body.getInt("v"))
        assertEquals(1717250000L, body.getLong("ts"))
        assertEquals("ytmusic", body.getString("cmd"))
        assertEquals(
            "https://music.youtube.com/watch?list=PLabc123&shuffle=1",
            body.getString("url"),
        )
        assertFalse(body.has("form"))
        assertFalse(body.has("id"))
    }

    @Test
    fun `publishYtMusic with song form posts v2 url with video id`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        publisher.publishYtMusic(Form.SONG, "dQw4w9WgXcQ")

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(2, body.getInt("v"))
        assertEquals("ytmusic", body.getString("cmd"))
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            body.getString("url"),
        )
        assertFalse(body.has("form"))
        assertFalse(body.has("id"))
    }

    @Test(expected = NtfyPublishException::class)
    fun `publishYtMusic throws on non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        publisher.publishYtMusic(Form.PLAYLIST, "PLabc123")
    }

    @Test
    fun `clock supplies ts in unix seconds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishYtMusic(Form.PLAYLIST, "PLxyz")

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue("ts must be positive", body.getLong("ts") > 0)
    }

    @Test
    fun `publishWaze posts v2 envelope with url`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        val wazePublisher = NtfyPublisher(
            client = OkHttpClient(),
            base = server.url("").toString().trimEnd('/'),
            topic = "test-topic",
            clock = { 1748779200L },
        )

        wazePublisher.publishWaze("https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/test-topic", recorded.path)

        val bodyJson = JSONObject(recorded.body.readUtf8())
        assertEquals(2, bodyJson.getInt("v"))
        assertEquals(1748779200L, bodyJson.getLong("ts"))
        assertEquals("waze", bodyJson.getString("cmd"))
        assertEquals("https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes", bodyJson.getString("url"))
        assertFalse(bodyJson.has("form"))
        assertFalse(bodyJson.has("id"))
    }
}
