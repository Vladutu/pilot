package com.vladutu.pilot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.destination.DestinationPipeline
import com.vladutu.pilot.meta.MetadataFetcher
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.radio.RadioBrowserClient

private sealed interface PilotRoute {
    data object Home : PilotRoute
    data class Category(val form: Form) : PilotRoute
    data object RadioSearch : PilotRoute
}

@Composable
fun PilotNavHost(
    publisher: NtfyPublisher,
    store: CatalogStore,
    metadataFetcher: MetadataFetcher,
    pipeline: DestinationPipeline,
    publishStatus: PublishStatusHolder,
    radioBrowserClient: RadioBrowserClient,
) {
    var route by remember { mutableStateOf<PilotRoute>(PilotRoute.Home) }

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
        )
        is PilotRoute.Category -> CategoryListScreen(
            form = r.form,
            publisher = publisher,
            store = store,
            pipeline = pipeline,
            publishStatus = publishStatus,
            onBack = { route = PilotRoute.Home },
            onOpenRadioSearch = { route = PilotRoute.RadioSearch },
        )
        is PilotRoute.RadioSearch -> RadioSearchScreen(
            client = radioBrowserClient,
            store = store,
            metadataFetcher = metadataFetcher,
            onBack = { route = PilotRoute.Category(Form.RADIO) },
        )
    }
}
