package com.vladutu.pilot.share

import com.vladutu.pilot.diagnostics.ResolverStats
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InAppMapsToWazeResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: InAppMapsToWazeResolver

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = InAppMapsToWazeResolver(OkHttpClient(), callTimeoutSec = 5L)
        ResolverStats.reset()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun resolve_followsRedirectToCoordinateUrl() = runBlocking {
        val coordPath = "/maps/search/44.116698,+24.186381"
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url(coordPath).toString()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>maps</html>"))

        val res = resolver.resolve(server.url("/abc123").toString())

        assertEquals("https://ul.waze.com/ul?ll=44.116698%2C24.186381&navigate=yes", res?.wazeUrl)
        assertEquals(2, server.requestCount)
        assertEquals(1, ResolverStats.count(ResolverStats.Outcome.IN_APP_NETWORK))

        val first = server.takeRequest()
        assertEquals("GET", first.method)
        assertTrue("expected a browser User-Agent", first.getHeader("User-Agent")!!.contains("Mozilla"))
    }

    @Test
    fun resolve_returnsNull_whenResolvedUrlHasNoCoords() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/maps/place/Eiffel").toString()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>place</html>"))

        assertNull(resolver.resolve(server.url("/abc").toString()))
        assertEquals(1, ResolverStats.count(ResolverStats.Outcome.FALLBACK_NO_COORDS))
    }

    @Test
    fun resolve_usesFastPath_whenSharedUrlAlreadyHasCoords() = runBlocking {
        val shared = "https://www.google.com/maps/place/X/@52.5,13.4,17z/data=!3d52.5162746!4d13.3777041"

        val res = resolver.resolve(shared)

        assertEquals("https://ul.waze.com/ul?ll=52.5162746%2C13.3777041&navigate=yes", res?.wazeUrl)
        assertEquals(shared, res?.resolvedUrl)
        assertEquals("no network call expected on fast path", 0, server.requestCount)
        assertEquals(1, ResolverStats.count(ResolverStats.Outcome.IN_APP_FAST))
    }

    @Test
    fun resolve_returnsNull_onNetworkFailure() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertNull(resolver.resolve(server.url("/abc").toString()))
        assertEquals(1, ResolverStats.count(ResolverStats.Outcome.FALLBACK_NETWORK))
    }
}
