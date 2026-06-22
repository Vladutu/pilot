package com.vladutu.pilot.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.destination.DestinationPipeline
import com.vladutu.pilot.diagnostics.DiagnosticsActivity
import com.vladutu.pilot.discover.DiscoverCategoryStore
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.radio.RadioBrowserClient
import com.vladutu.pilot.settings.SettingsStore

private sealed interface PilotRoute {
    data object Home : PilotRoute
    data class Category(val form: Form) : PilotRoute
    data object RadioSearch : PilotRoute
    data object DiscoverCategories : PilotRoute
    data object Settings : PilotRoute
}

@Composable
fun PilotNavHost(
    publisher: NtfyPublisher,
    store: CatalogStore,
    discoverStore: DiscoverCategoryStore,
    metadataFetcher: MetadataFetcher,
    pipeline: DestinationPipeline,
    publishStatus: PublishStatusHolder,
    radioBrowserClient: RadioBrowserClient,
    settingsStore: SettingsStore,
    startUnpaired: Boolean = false,
) {
    val context = LocalContext.current
    val radioCountry by settingsStore.radioCountryFlow.collectAsState(initial = null)
    var route by remember {
        mutableStateOf<PilotRoute>(if (startUnpaired) PilotRoute.Settings else PilotRoute.Home)
    }

    // Hardware back: any spoke returns to the hub; on the hub, let the system handle it.
    BackHandler(enabled = route != PilotRoute.Home) {
        route = when (route) {
            is PilotRoute.RadioSearch -> PilotRoute.Category(Form.RADIO)
            else -> PilotRoute.Home
        }
    }

    when (val r = route) {
        is PilotRoute.Home -> HomeHub(
            publishStatus = publishStatus,
            onOpenCategory = { route = PilotRoute.Category(it) },
            onOpenDiscover = { route = PilotRoute.DiscoverCategories },
            onOpenSettings = { route = PilotRoute.Settings },
        )
        is PilotRoute.Category -> CategoryListScreen(
            form = r.form,
            publisher = publisher,
            store = store,
            pipeline = pipeline,
            publishStatus = publishStatus,
            radioCountrySet = radioCountry != null,
            onBack = { route = PilotRoute.Home },
            onOpenRadioSearch = { route = PilotRoute.RadioSearch },
        )
        is PilotRoute.RadioSearch -> RadioSearchScreen(
            client = radioBrowserClient,
            countryCode = radioCountry?.code,
            store = store,
            metadataFetcher = metadataFetcher,
            onBack = { route = PilotRoute.Category(Form.RADIO) },
        )
        is PilotRoute.DiscoverCategories -> DiscoverCategoriesScreen(
            publisher = publisher,
            store = discoverStore,
            publishStatus = publishStatus,
            onBack = { route = PilotRoute.Home },
        )
        is PilotRoute.Settings -> SettingsScreen(
            settingsStore = settingsStore,
            radioBrowserClient = radioBrowserClient,
            onBack = { route = PilotRoute.Home },
            onOpenDiagnostics = {
                context.startActivity(Intent(context, DiagnosticsActivity::class.java))
            },
        )
    }
}
