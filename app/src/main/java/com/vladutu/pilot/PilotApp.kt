package com.vladutu.pilot

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.config.Config
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.net.NtfyPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

private val Application.catalogDataStore: DataStore<Preferences> by preferencesDataStore(name = "catalog")

class PilotApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val httpClient: OkHttpClient by lazy { OkHttpClient() }

    val catalogStore: CatalogStore by lazy { CatalogStore(catalogDataStore) }

    val ntfyPublisher: NtfyPublisher by lazy {
        NtfyPublisher(client = httpClient, base = Config.NTFY_BASE, topic = Config.NTFY_TOPIC)
    }

    val metadataFetcher: MetadataFetcher by lazy {
        MetadataFetcher(client = httpClient, cacheDir = cacheDir)
    }
}
