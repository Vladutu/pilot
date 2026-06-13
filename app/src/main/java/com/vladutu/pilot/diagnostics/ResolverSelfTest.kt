package com.vladutu.pilot.diagnostics

import com.vladutu.pilot.share.MapsResolver

/**
 * Manual canary that resolves known Google Maps links through the in-app resolver and reports
 * whether coordinate extraction still works. Run on demand from [DiagnosticsActivity] — never on a
 * hot path — so a transient offline launch can't raise a false "Google changed their links" alarm.
 *
 * It distinguishes a real format change (resolver fetched fine but found no coordinates → actionable)
 * from a benign network failure (inconclusive) by reading which [ResolverStats.Outcome] the resolver
 * recorded for each link.
 */
object ResolverSelfTest {

    // Representative links. Add more formats (place, DMS) here as stable canaries become available.
    val CANARY_LINKS = listOf(
        "https://maps.app.goo.gl/GXMYgYUYF98yN3va8", // pin link → /maps/search/<lat>,<lng>
    )

    data class Report(val text: String, val formatDriftSuspected: Boolean)

    suspend fun run(resolver: MapsResolver, links: List<String> = CANARY_LINKS): Report {
        val sb = StringBuilder()
        var drift = false
        for (link in links) {
            val before = ResolverStats.snapshot()
            val res = resolver.resolve(link)
            val after = ResolverStats.snapshot()
            val outcome = ResolverStats.Outcome.values()
                .firstOrNull { (after[it] ?: 0) > (before[it] ?: 0) }

            when {
                res != null ->
                    sb.appendLine("PASS  $link\n        → ${res.wazeUrl}")
                outcome == ResolverStats.Outcome.FALLBACK_NETWORK ->
                    sb.appendLine("SKIP  $link\n        network error (check connectivity) — inconclusive")
                else -> {
                    drift = true
                    sb.appendLine("FAIL  $link\n        no coordinates in resolved URL — link format may have changed")
                }
            }
        }
        sb.append(
            if (drift) "⚠ Format drift suspected — update MapsCoordinateExtractor."
            else "Canaries OK (or inconclusive)."
        )
        val report = sb.toString()
        DiagnosticLog.i(TAG, "self-test:\n$report")
        return Report(report, drift)
    }

    private const val TAG = "ResolverSelfTest"
}
