package com.vladutu.pilot.radio

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RadioBrowserClientTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        base = server.url("").toString().trimEnd('/')
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `searchUrl includes RO ordering and broken filter`() {
        val url = RadioBrowserClient.searchUrl(base, query = null)
        assertTrue(url.contains("countrycode=RO"))
        assertTrue(url.contains("order=votes"))
        assertTrue(url.contains("reverse=true"))
        assertTrue(url.contains("limit=50"))
        assertTrue(url.contains("hidebroken=true"))
    }

    @Test fun `searchUrl adds name filter when query given`() {
        val url = RadioBrowserClient.searchUrl(base, query = "kiss fm")
        assertTrue(url, url.contains("name=kiss%20fm") || url.contains("name=kiss+fm"))
    }

    @Test fun `searchRomania resolves a server then returns mapped stations`() = runTest {
        // /json/servers, then /json/stats (health), then the search response.
        server.enqueue(MockResponse().setBody("""[{"name":"a"}]"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        server.enqueue(
            MockResponse().setBody(
                """[{"stationuuid":"u1","name":"Europa FM","url_resolved":"https://live/europafm.mp3","favicon":"https://f/p.png","lastcheckok":1}]"""
            )
        )
        val resolver = RadioBrowserServerResolver(
            client = OkHttpClient(),
            serversUrl = server.url("/json/servers").toString(),
            baseFor = { base },
        )
        val client = RadioBrowserClient(OkHttpClient(), resolver)
        val stations = client.searchRomania(query = null)
        assertEquals(1, stations.size)
        assertEquals("Europa FM", stations[0].name)
        assertEquals("https://live/europafm.mp3", stations[0].streamUrl)
    }

    @Test(expected = RadioBrowserException::class)
    fun `searchRomania throws when no server resolves`() = runTest {
        server.enqueue(MockResponse().setBody("""[]""")) // empty server list → resolve() null
        val resolver = RadioBrowserServerResolver(
            client = OkHttpClient(),
            serversUrl = server.url("/json/servers").toString(),
            baseFor = { base },
        )
        RadioBrowserClient(OkHttpClient(), resolver).searchRomania(query = null)
    }
}
