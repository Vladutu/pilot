package com.vladutu.pilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vladutu.pilot.ui.PilotNavHost
import com.vladutu.pilot.ui.theme.PilotTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PilotApp
        setContent {
            PilotTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PilotNavHost(
                        publisher = app.ntfyPublisher,
                        store = app.catalogStore,
                        discoverStore = app.discoverCategoryStore,
                        metadataFetcher = app.metadataFetcher,
                        pipeline = app.destinationPipeline,
                        publishStatus = app.publishStatus,
                        radioBrowserClient = app.radioBrowserClient,
                    )
                }
            }
        }
    }
}
