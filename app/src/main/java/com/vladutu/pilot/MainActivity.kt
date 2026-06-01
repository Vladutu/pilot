package com.vladutu.pilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vladutu.pilot.ui.CatalogScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PilotApp
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CatalogScreen(
                        publisher = app.ntfyPublisher,
                        store = app.catalogStore,
                        metadataFetcher = app.metadataFetcher,
                        pipeline = app.destinationPipeline,
                    )
                }
            }
        }
    }
}
