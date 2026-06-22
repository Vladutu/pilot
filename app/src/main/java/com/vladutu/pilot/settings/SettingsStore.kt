package com.vladutu.pilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vladutu.pilot.radio.RadioCountry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Pilot's on-disk app settings: the paired ntfy topic and the radio country. Because they
 * are persisted, both are one-time choices reloaded on every launch. Mirrors the
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

    /**
     * Emits the chosen radio country, or null when unset. Without a country the radio
     * feature can't search radio-browser, so the UI prompts the user to pick one in Settings.
     */
    val radioCountryFlow: Flow<RadioCountry?> = dataStore.data.map { prefs ->
        val code = prefs[RADIO_COUNTRY_CODE]?.takeIf { it.isNotBlank() } ?: return@map null
        val name = prefs[RADIO_COUNTRY_NAME]?.takeIf { it.isNotBlank() } ?: code
        RadioCountry(code = code, name = name)
    }

    /** Stores the chosen radio country (code drives search; name is shown in Settings). */
    suspend fun setRadioCountry(country: RadioCountry) {
        dataStore.edit { prefs ->
            prefs[RADIO_COUNTRY_CODE] = country.code
            prefs[RADIO_COUNTRY_NAME] = country.name
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("ntfy_topic")
        val RADIO_COUNTRY_CODE = stringPreferencesKey("radio_country_code")
        val RADIO_COUNTRY_NAME = stringPreferencesKey("radio_country_name")
    }
}
