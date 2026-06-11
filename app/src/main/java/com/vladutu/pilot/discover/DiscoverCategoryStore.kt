package com.vladutu.pilot.discover

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Locally-authored Discover keyword categories. This list is Pilot's own — deleting
 * here does not touch the car; the headunit manages its copy via long-press. Tapping
 * a category in the UI re-publishes it (the live-only delivery escape hatch).
 */
class DiscoverCategoryStore(private val dataStore: DataStore<Preferences>) {

    private val mutex = Mutex()

    fun categories(): Flow<List<String>> = dataStore.data.map { prefs -> decode(prefs[KEY]) }

    /** Idempotent: re-adding an existing keyword (any casing) is a no-op. */
    suspend fun add(keyword: String) {
        val cleaned = keyword.trim()
        if (cleaned.isEmpty()) return
        mutate { current ->
            if (current.any { it.equals(cleaned, ignoreCase = true) }) current else current + cleaned
        }
    }

    suspend fun delete(keyword: String) = mutate { current ->
        current.filterNot { it.equals(keyword, ignoreCase = true) }
    }

    private suspend fun mutate(transform: (List<String>) -> List<String>) = mutex.withLock {
        dataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(transform(decode(prefs[KEY])))
        }
    }

    private fun decode(blob: String?): List<String> {
        if (blob.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(blob)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("discover_categories")
        val json = Json { ignoreUnknownKeys = true }
    }
}
