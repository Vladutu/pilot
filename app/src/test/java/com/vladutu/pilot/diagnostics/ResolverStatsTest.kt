package com.vladutu.pilot.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolverStatsTest {

    @Before
    fun setUp() = ResolverStats.reset()

    @Test
    fun recordsAndCounts() {
        ResolverStats.record(ResolverStats.Outcome.IN_APP_FAST)
        ResolverStats.record(ResolverStats.Outcome.IN_APP_FAST)
        ResolverStats.record(ResolverStats.Outcome.FALLBACK_NO_COORDS)

        assertEquals(2, ResolverStats.count(ResolverStats.Outcome.IN_APP_FAST))
        assertEquals(1, ResolverStats.count(ResolverStats.Outcome.FALLBACK_NO_COORDS))
        assertEquals(0, ResolverStats.count(ResolverStats.Outcome.FALLBACK_NETWORK))
    }

    @Test
    fun snapshotCoversEveryOutcome() {
        val snap = ResolverStats.snapshot()
        assertEquals(ResolverStats.Outcome.values().size, snap.size)
    }

    @Test
    fun summary_emptyState() {
        assertEquals("Maps resolver: no resolutions yet", ResolverStats.summary())
    }

    @Test
    fun summary_reflectsCounts() {
        ResolverStats.record(ResolverStats.Outcome.IN_APP_NETWORK)
        ResolverStats.record(ResolverStats.Outcome.FALLBACK_NO_COORDS)
        val s = ResolverStats.summary()
        assertTrue("got: $s", s.contains("1/2 in-app"))
        assertTrue("got: $s", s.contains("1 no-coords"))
    }

    @Test
    fun reset_zeroesCounters() {
        ResolverStats.record(ResolverStats.Outcome.IN_APP_FAST)
        ResolverStats.reset()
        assertEquals(0, ResolverStats.count(ResolverStats.Outcome.IN_APP_FAST))
    }
}
