package com.vladutu.pilot.catalog

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CatalogStore(private val dataStore: DataStore<Preferences>) {

    val entries: Flow<List<CatalogEntry>> = dataStore.data.map { prefs ->
        decode(prefs[KEY]).sortedByDescending { it.savedAt }
    }

    suspend fun upsert(entry: CatalogEntry) = mutate { current ->
        current.filterNot { it.form == entry.form && it.id == entry.id } + entry
    }

    suspend fun updateMeta(form: Form, id: String, title: String, imagePath: String?) = mutate { current ->
        current.map {
            if (it.form == form && it.id == id) it.copy(title = title, imagePath = imagePath) else it
        }
    }

    suspend fun rename(form: Form, id: String, newTitle: String) = mutate { current ->
        current.map { if (it.form == form && it.id == id) it.copy(title = newTitle) else it }
    }

    suspend fun delete(form: Form, id: String) = mutate { current ->
        current.filterNot { it.form == form && it.id == id }
    }

    private suspend fun mutate(transform: (List<CatalogEntry>) -> List<CatalogEntry>) {
        dataStore.edit { prefs ->
            val updated = transform(decode(prefs[KEY]))
            prefs[KEY] = json.encodeToString(updated)
        }
    }

    private fun decode(blob: String?): List<CatalogEntry> {
        if (blob.isNullOrEmpty()) return emptyList()
        return json.decodeFromString(blob)
    }

    private companion object {
        val KEY = stringPreferencesKey("catalog_json")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
