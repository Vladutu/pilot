package com.vladutu.pilot.diagnostics

import com.vladutu.pilot.share.MapsResolution
import com.vladutu.pilot.share.MapsResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolverSelfTestTest {

    private val link = listOf("https://maps.app.goo.gl/x")

    @Before
    fun setUp() = ResolverStats.reset()

    @Test
    fun pass_whenResolverReturnsResolution() = runBlocking {
        val resolver = object : MapsResolver {
            override suspend fun resolve(googleMapsUrl: String) =
                MapsResolution("https://ul.waze.com/ul?ll=1.0%2C2.0&navigate=yes", "https://maps/search/1.0,2.0")
        }

        val report = ResolverSelfTest.run(resolver, link)

        assertFalse(report.formatDriftSuspected)
        assertTrue("got: ${report.text}", report.text.contains("PASS"))
    }

    @Test
    fun driftSuspected_whenResolverFindsNoCoords() = runBlocking {
        val resolver = object : MapsResolver {
            override suspend fun resolve(googleMapsUrl: String): MapsResolution? {
                ResolverStats.record(ResolverStats.Outcome.FALLBACK_NO_COORDS)
                return null
            }
        }

        val report = ResolverSelfTest.run(resolver, link)

        assertTrue(report.formatDriftSuspected)
        assertTrue("got: ${report.text}", report.text.contains("FAIL"))
    }

    @Test
    fun networkFailure_isInconclusive_notDrift() = runBlocking {
        val resolver = object : MapsResolver {
            override suspend fun resolve(googleMapsUrl: String): MapsResolution? {
                ResolverStats.record(ResolverStats.Outcome.FALLBACK_NETWORK)
                return null
            }
        }

        val report = ResolverSelfTest.run(resolver, link)

        assertFalse(report.formatDriftSuspected)
        assertTrue("got: ${report.text}", report.text.contains("SKIP"))
    }
}
