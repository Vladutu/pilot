package com.vladutu.pilot

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.config.Config
import com.vladutu.pilot.destination.DestinationPipeline
import com.vladutu.pilot.diagnostics.DiagnosticLog
import com.vladutu.pilot.discover.DiscoverCategoryStore
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.share.MapsToWazeConverter
import com.vladutu.pilot.ui.PublishStatusHolder
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

private val Application.catalogDataStore: DataStore<Preferences> by preferencesDataStore(name = "catalog")
private val Application.discoverDataStore: DataStore<Preferences> by preferencesDataStore(name = "discover_categories")

class PilotApp : Application() {

    private val crashHandler = CoroutineExceptionHandler { ctx, t ->
        // Swallow-and-log: capture what would have crashed the process so we can diagnose it
        // after the fact via DiagnosticsActivity. Defense in depth for paths that miss a catch.
        DiagnosticLog.e("AppScope", "uncaught coroutine exception in $ctx", t)
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashHandler)

    override fun onCreate() {
        super.onCreate()
        DiagnosticLog.init(this)
        DiagnosticLog.i("App", "PilotApp.onCreate (pid=${android.os.Process.myPid()})")
    }

    val httpClient: OkHttpClient by lazy { OkHttpClient() }

    val catalogStore: CatalogStore by lazy { CatalogStore(catalogDataStore) }

    val discoverCategoryStore: DiscoverCategoryStore by lazy { DiscoverCategoryStore(discoverDataStore) }

    val ntfyPublisher: NtfyPublisher by lazy {
        NtfyPublisher(client = httpClient, base = Config.NTFY_BASE, topic = Config.NTFY_TOPIC)
    }

    val publishStatus: PublishStatusHolder by lazy { PublishStatusHolder() }

    val metadataFetcher: MetadataFetcher by lazy {
        MetadataFetcher(client = httpClient, cacheDir = cacheDir)
    }

    val radioServerResolver: com.vladutu.pilot.radio.RadioBrowserServerResolver by lazy {
        com.vladutu.pilot.radio.RadioBrowserServerResolver(client = httpClient)
    }

    val radioBrowserClient: com.vladutu.pilot.radio.RadioBrowserClient by lazy {
        com.vladutu.pilot.radio.RadioBrowserClient(client = httpClient, resolver = radioServerResolver)
    }

    val mapsToWazeConverter: MapsToWazeConverter by lazy {
        MapsToWazeConverter(client = httpClient, endpoint = Config.WAZE_CONVERTER_URL)
    }

    val destinationPipeline: DestinationPipeline by lazy {
        DestinationPipeline(
            converter = mapsToWazeConverter,
            catalogStore = catalogStore,
            publisher = ntfyPublisher,
            metadataFetcher = metadataFetcher,
            backgroundScope = applicationScope,
            processStateProbe = { com.vladutu.pilot.diagnostics.ProcessState.describe(this) },
        )
    }
}
