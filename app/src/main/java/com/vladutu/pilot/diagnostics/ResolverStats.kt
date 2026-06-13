package com.vladutu.pilot.diagnostics

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory counters for in-app Maps→Waze resolution outcomes, surfaced in [DiagnosticsActivity].
 *
 * The signal to watch is [Outcome.FALLBACK_NO_COORDS] climbing: it means the resolver fetched
 * Google fine but found no coordinates in the resolved URL — i.e. Google likely changed the link
 * format and [com.vladutu.pilot.share.MapsCoordinateExtractor] needs updating.
 * [Outcome.FALLBACK_NETWORK] is benign (connectivity).
 *
 * Counters are process-lifetime only (reset on restart); the durable record is the diagnostic log.
 * Kept separate from [DiagnosticLog] so the log stays a pure append-only writer.
 */
object ResolverStats {

    enum class Outcome {
        IN_APP_FAST,        // coordinates already in the shared URL / subject, no network needed
        IN_APP_NETWORK,     // coordinates found after following redirects
        FALLBACK_NO_COORDS, // fetched ok but no coords and no place-id → THE format-drift signal
        FALLBACK_PLACE_ID,  // resolved URL had only a place-id/ftid (no lat,lng) → needs Places API (papko)
        FALLBACK_NETWORK,   // network / bad-URL failure → fell back to converter (benign)
    }

    private val counts = ConcurrentHashMap<Outcome, AtomicInteger>()

    fun record(outcome: Outcome) {
        counts.computeIfAbsent(outcome) { AtomicInteger() }.incrementAndGet()
    }

    fun count(outcome: Outcome): Int = counts[outcome]?.get() ?: 0

    fun snapshot(): Map<Outcome, Int> = Outcome.values().associateWith { count(it) }

    fun reset() = counts.clear()

    /** One-line human summary for the diagnostics screen. */
    fun summary(): String {
        val inApp = count(Outcome.IN_APP_FAST) + count(Outcome.IN_APP_NETWORK)
        val noCoords = count(Outcome.FALLBACK_NO_COORDS)
        val placeId = count(Outcome.FALLBACK_PLACE_ID)
        val netFail = count(Outcome.FALLBACK_NETWORK)
        val total = inApp + noCoords + placeId + netFail
        if (total == 0) return "Maps resolver: no resolutions yet"
        return "Maps resolver: $inApp/$total in-app " +
            "(fast ${count(Outcome.IN_APP_FAST)}, net ${count(Outcome.IN_APP_NETWORK)}) · " +
            "fallback: $placeId place-id, $noCoords no-coords, $netFail net-fail"
    }
}
