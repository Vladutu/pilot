package com.vladutu.pilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.vladutu.pilot.config.Config
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.ui.CatalogScreen
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {

    private val client by lazy { OkHttpClient() }
    private val publisher by lazy {
        NtfyPublisher(client = client, base = Config.NTFY_BASE, topic = Config.NTFY_TOPIC)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CatalogScreen(publisher = publisher)
                }
            }
        }
    }
}
