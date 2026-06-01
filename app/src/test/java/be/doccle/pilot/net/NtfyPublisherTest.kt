package be.doccle.pilot.net

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `publishYtMusicPlaylist posts expected JSON body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))

        publisher.publishYtMusicPlaylist("PLabc123")

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/test-topic", recorded.path)
        assertEquals("application/json", recorded.getHeader("Content-Type"))

        val body = JSONObject(recorded.body.readUtf8())
        assertEquals(1, body.getInt("v"))
        assertEquals(1717250000L, body.getLong("ts"))
        assertEquals("ytmusic", body.getString("cmd"))
        assertEquals("playlist", body.getString("form"))
        assertEquals("PLabc123", body.getString("id"))
    }

    @Test(expected = NtfyPublishException::class)
    fun `publishYtMusicPlaylist throws on non-2xx response`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        publisher.publishYtMusicPlaylist("PLabc123")
    }

    @Test
    fun `clock supplies ts in unix seconds`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher.publishYtMusicPlaylist("PLxyz")

        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue("ts must be positive", body.getLong("ts") > 0)
    }
}
