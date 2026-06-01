package com.vladutu.pilot.share

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class MapsToWazeConverterTest {

    private lateinit var server: MockWebServer
    private lateinit var converter: MapsToWazeConverter

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        converter = MapsToWazeConverter(
            client = OkHttpClient(),
            endpoint = server.url("/").toString(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun convert_returnsLocationHeader_on302() = runBlocking {
        val expected = "https://ul.waze.com/ul?ll=52.5162746%2C13.3777041&navigate=yes"
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", expected))

        val result = converter.convert("https://www.google.com/maps/place/Brandenburg+Gate")

        assertEquals(expected, result)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue("body should be form-encoded url=...; got: $body", body.startsWith("url="))
        assertTrue("body should contain the Maps URL", body.contains("google.com"))
    }

    @Test
    fun convert_doesNotFollowRedirect() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/should-not-be-fetched"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("should not be reached"))

        converter.convert("https://www.google.com/maps/place/X")

        assertEquals(1, server.requestCount)
    }

    @Test(expected = WazeConversionException::class)
    fun convert_throws_on200ErrorPage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>error</html>"))
        converter.convert("https://www.google.com/maps/place/X")
        fail("expected WazeConversionException")
    }

    @Test(expected = WazeConversionException::class)
    fun convert_throws_on302WithoutLocation() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302))
        converter.convert("https://www.google.com/maps/place/X")
        fail("expected WazeConversionException")
    }

    @Test(expected = WazeConversionException::class)
    fun convert_throws_on5xx() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        converter.convert("https://www.google.com/maps/place/X")
        fail("expected WazeConversionException")
    }
}
