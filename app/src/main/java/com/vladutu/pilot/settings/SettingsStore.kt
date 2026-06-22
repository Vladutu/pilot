package com.vladutu.pilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Pilot's on-disk app settings. Currently just the paired ntfy topic. Because it is
 * persisted, pairing is one-time — the topic is reloaded on every launch; re-pairing is
 * only needed if the user re-pairs or Copilot regenerates the topic. Mirrors the
 * CatalogStore / DiscoverCategoryStore DataStore pattern.
 */
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    /** Emits the stored topic, or null when unset/blank (not yet paired). */
    val topicFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY]?.takeIf { it.isNotBlank() }
    }

    /** Stores [topic] verbatim. Callers validate via TopicPairing first. */
    suspend fun setTopic(topic: String) {
        dataStore.edit { prefs -> prefs[KEY] = topic }
    }

    private companion object {
        val KEY = stringPreferencesKey("ntfy_topic")
    }
}
