package com.vladutu.pilot.radio

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RadioBrowserServerResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var base: String

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        base = server.url("").toString().trimEnd('/')
    }

    @After fun tearDown() { server.shutdown() }

    /** All candidate names resolve to the one MockWebServer. */
    private fun resolver() = RadioBrowserServerResolver(
        client = OkHttpClient(),
        serversUrl = server.url("/json/servers").toString(),
        baseFor = { base },
    )

    @Test fun `picks first healthy server`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"name":"a"},{"name":"b"}]"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // stats for a → healthy
        val r = resolver()
        assertEquals(base, r.resolve())
        assertEquals(2, server.requestCount) // servers + one stats
    }

    @Test fun `falls back past an unhealthy server`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"name":"a"},{"name":"b"}]"""))
        server.enqueue(MockResponse().setResponseCode(500)) // stats for a → unhealthy
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}")) // stats for b → healthy
        val r = resolver()
        assertEquals(base, r.resolve())
        assertEquals(3, server.requestCount) // servers + two stats
    }

    @Test fun `caches the resolved server`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"name":"a"}]"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val r = resolver()
        assertEquals(base, r.resolve())
        assertEquals(base, r.resolve()) // second call uses cache
        assertEquals(2, server.requestCount) // not 4 — no re-fetch
    }

    @Test fun `returns null when all servers unhealthy`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"name":"a"},{"name":"b"}]"""))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        assertNull(resolver().resolve())
    }

    @Test fun `returns null when server list is empty`() = runTest {
        server.enqueue(MockResponse().setBody("""[]"""))
        assertNull(resolver().resolve())
    }
}
