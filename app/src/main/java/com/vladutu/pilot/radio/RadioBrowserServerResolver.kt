package com.vladutu.pilot.radio

import com.vladutu.pilot.diagnostics.DiagnosticLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Resolves a healthy radio-browser mirror at runtime. Never hardcode a server —
 * mirrors come and go (spec §3). Strategy: GET /json/servers → for each candidate,
 * health-check /json/stats → cache the first that responds 2xx, fall back otherwise.
 */
class RadioBrowserServerResolver(
    private val client: OkHttpClient,
    private val serversUrl: String = "https://all.api.radio-browser.info/json/servers",
    private val baseFor: (String) -> String = { "https://$it" },
) {
    @Volatile private var cachedBase: String? = null

    suspend fun resolve(): String? = withContext(Dispatchers.IO) {
        cachedBase?.let { return@withContext it }
        val names = fetchServerNames() ?: return@withContext null
        for (name in names) {
            val candidate = baseFor(name)
            if (isHealthy(candidate)) {
                DiagnosticLog.i(TAG, "resolved radio-browser server: $candidate")
                cachedBase = candidate
                return@withContext candidate
            }
        }
        DiagnosticLog.w(TAG, "no healthy radio-browser server among ${names.size} candidates")
        null
    }

    private fun fetchServerNames(): List<String>? {
        val req = Request.Builder().url(serversUrl).header("User-Agent", USER_AGENT).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val arr = JSONArray(body)
                (0 until arr.length()).mapNotNull {
                    arr.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() }
                }
            }
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "fetch server list failed", e)
            null
        }
    }

    private fun isHealthy(base: String): Boolean {
        val req = Request.Builder().url("$base/json/stats").header("User-Agent", USER_AGENT).build()
        return try {
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            DiagnosticLog.w(TAG, "health-check failed for $base", e)
            false
        }
    }

    companion object {
        const val USER_AGENT = "Copilot/1.0"
        private const val TAG = "RadioResolver"
    }
}
