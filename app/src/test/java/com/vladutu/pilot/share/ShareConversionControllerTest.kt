package com.vladutu.pilot.share

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareConversionControllerTest {

    private val wazeUrl = "https://ul.waze.com/ul?ll=44.33%2C23.77&navigate=yes"
    private val mapsUrl = "https://maps.app.goo.gl/abc123"

    private class FakeResolver(private val result: MapsResolution?) : MapsResolver {
        var calls = 0
        override suspend fun resolve(googleMapsUrl: String, hints: List<String>): MapsResolution? {
            calls++
            return result
        }
    }

    /** Throws [WazeConversionException] for the first [failuresBeforeSuccess] calls, then succeeds. */
    private class FakeConverter(
        private val failuresBeforeSuccess: Int,
        private val successUrl: String,
    ) : WazeConverter {
        var calls = 0
        override suspend fun convert(googleMapsUrl: String): String {
            calls++
            if (calls <= failuresBeforeSuccess) throw WazeConversionException("simulated papko 200")
            return successUrl
        }
    }

    @Test
    fun inAppSuccess_resolvesWithoutTouchingConverter() = runTest {
        val resolver = FakeResolver(MapsResolution(wazeUrl, resolvedUrl = "https://www.google.com/maps/place/X"))
        val converter = FakeConverter(failuresBeforeSuccess = 0, successUrl = "unused")
        val controller = ShareConversionController(resolver, converter, retryDelayMs = 1_000L)

        val outcome = controller.run(mapsUrl, subject = "Dedeman")

        assertTrue("expected Resolved, got $outcome", outcome is ConversionOutcome.Resolved)
        assertEquals(wazeUrl, (outcome as ConversionOutcome.Resolved).wazeUrl)
        assertEquals(0, converter.calls)
        assertEquals(ConversionUiState.Working, controller.state.value) // never showed the card
    }

    @Test
    fun papkoFailsThenSucceeds_retriesWithOneSecondBackoff() = runTest {
        val resolver = FakeResolver(null) // force papko fallback
        val converter = FakeConverter(failuresBeforeSuccess = 2, successUrl = wazeUrl)
        val controller = ShareConversionController(resolver, converter, retryDelayMs = 1_000L)

        val outcome = controller.run(mapsUrl, subject = "Dedeman")

        assertTrue("expected Resolved, got $outcome", outcome is ConversionOutcome.Resolved)
        assertEquals(wazeUrl, (outcome as ConversionOutcome.Resolved).wazeUrl)
        assertEquals(3, converter.calls) // 2 failures + 1 success
        assertEquals(2_000L, testScheduler.currentTime) // two 1s backoffs between the three attempts

        val state = controller.state.value
        assertTrue("expected Retrying, got $state", state is ConversionUiState.Retrying)
        assertEquals(2, (state as ConversionUiState.Retrying).attempt) // 2 failures recorded
        assertEquals("Dedeman", state.label)
    }

    @Test
    fun convertingCard_appearsAfterGrace_whenResolveIsSlow() = runTest {
        val slowResolver = object : MapsResolver {
            override suspend fun resolve(googleMapsUrl: String, hints: List<String>): MapsResolution? {
                delay(2_000L) // outlasts the grace window
                return null
            }
        }
        val converter = FakeConverter(failuresBeforeSuccess = 0, successUrl = wazeUrl)
        val controller = ShareConversionController(slowResolver, converter, retryDelayMs = 1_000L, graceDelayMs = 450L)

        val job = launch { controller.run(mapsUrl, subject = "Dedeman") }
        advanceTimeBy(500L) // past the 450ms grace, still inside the 2s resolve
        runCurrent()

        assertEquals(ConversionUiState.Converting("Dedeman"), controller.state.value)
        job.cancel()
        job.join()
    }

    @Test
    fun fastInApp_neverShowsConvertingCard() = runTest {
        // Resolves well within the grace window → stays invisible (Working) the whole time.
        val resolver = FakeResolver(MapsResolution(wazeUrl, resolvedUrl = "https://www.google.com/maps/place/X"))
        val converter = FakeConverter(failuresBeforeSuccess = 0, successUrl = "unused")
        val controller = ShareConversionController(resolver, converter, retryDelayMs = 1_000L, graceDelayMs = 450L)

        val outcome = controller.run(mapsUrl, subject = "Dedeman")

        assertTrue(outcome is ConversionOutcome.Resolved)
        assertEquals(ConversionUiState.Working, controller.state.value) // grace never elapsed
    }

    @Test
    fun cancellation_stopsLoop_andNeverResolves() = runTest {
        val resolver = FakeResolver(null)
        val converter = FakeConverter(failuresBeforeSuccess = Int.MAX_VALUE, successUrl = wazeUrl)
        val controller = ShareConversionController(resolver, converter, retryDelayMs = 1_000L)

        var outcome: ConversionOutcome? = null
        val job = launch { outcome = controller.run(mapsUrl, subject = "X") }

        advanceTimeBy(3_500L) // let a few attempts run
        runCurrent()
        assertTrue("expected the card to be showing", controller.state.value is ConversionUiState.Retrying)
        val callsBeforeStop = converter.calls

        job.cancel()
        job.join()

        assertNull("a cancelled loop must not produce a resolution", outcome)
        // no further attempts after cancellation
        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(callsBeforeStop, converter.calls)
    }

    @Test
    fun nonMapsShare_delegatesToService_withoutResolvingOrConverting() = runTest {
        val resolver = FakeResolver(null)
        val converter = FakeConverter(failuresBeforeSuccess = 0, successUrl = "unused")
        val controller = ShareConversionController(resolver, converter, retryDelayMs = 1_000L)

        val outcome = controller.run("https://music.youtube.com/watch?v=abc", subject = "Song")

        assertTrue("expected DelegateToService, got $outcome", outcome is ConversionOutcome.DelegateToService)
        assertEquals(0, resolver.calls)
        assertEquals(0, converter.calls)
    }
}
