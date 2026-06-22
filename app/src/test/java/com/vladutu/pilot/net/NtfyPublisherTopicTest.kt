package com.vladutu.pilot.net

import com.vladutu.pilot.catalog.Form
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class NtfyPublisherTopicTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    private fun publisher(topic: () -> String?) = NtfyPublisher(
        client = OkHttpClient(),
        base = server.url("").toString().trimEnd('/'),
        topicProvider = topic,
        clock = { 12345L },
        maxRetries = 0,
        retryBaseDelayMs = 1L,
    )

    @Test fun `null topic fails fast without any network call`() = runTest {
        try {
            publisher { null }.publishCategory("Workout")
            fail("expected NtfyPublishException")
        } catch (e: NtfyPublishException) {
            assertTrue(e.message?.contains("not paired") == true)
        }
        assertEquals("must not hit the network when unpaired", 0, server.requestCount)
    }

    @Test fun `blank topic fails fast`() = runTest {
        try {
            publisher { "   " }.publishCategory("Workout")
            fail("expected NtfyPublishException")
        } catch (e: NtfyPublishException) {
            assertTrue(e.message?.contains("not paired") == true)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `present topic is used in the request path`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200))
        publisher { "copilot-689e337645dc256a2b03d210d7b3c41b" }.publishCategory("Workout")
        val req = server.takeRequest()
        assertTrue(req.path!!.endsWith("/copilot-689e337645dc256a2b03d210d7b3c41b"))
        val body = JSONObject(req.body.readUtf8())
        assertEquals("category", body.getString("cmd"))
    }
}
