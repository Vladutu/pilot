package com.vladutu.pilot.radio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RadioCatalogTest {

    @get:Rule val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private fun store(): CatalogStore {
        val ds: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "catalog.preferences_pb") },
        )
        return CatalogStore(ds, clock = { 1000L })
    }

    private fun station(uuid: String, name: String) = RadioStation(
        stationUuid = uuid, name = name, streamUrl = "https://live/$uuid.mp3",
        faviconUrl = "https://f/$uuid.png", codec = "MP3", bitrate = 128, lastCheckOk = true,
    )

    @Test fun `addStation creates a RADIO entry keyed on stationUuid`() = runTest {
        val store = store()
        RadioCatalog.addStation(store, station("u1", "Europa FM"), imagePath = null)
        val entries = store.entries.first()
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals(Form.RADIO, e.form)
        assertEquals("u1", e.id)
        assertEquals("Europa FM", e.title)
        assertEquals("https://live/u1.mp3", e.url)
        assertEquals("https://f/u1.png", e.imageUrl)
    }

    @Test fun `addStation twice dedupes by stationUuid`() = runTest {
        val store = store()
        RadioCatalog.addStation(store, station("u1", "Europa FM"), imagePath = null)
        RadioCatalog.addStation(store, station("u1", "Europa FM (renamed)"), imagePath = null)
        val entries = store.entries.first().filter { it.form == Form.RADIO }
        assertEquals(1, entries.size)
        assertEquals("Europa FM (renamed)", entries[0].title)
    }

    @Test fun `addManual keys on sha1 of stream url`() = runTest {
        val store = store()
        RadioCatalog.addManual(store, streamUrl = "https://live/manual.mp3", title = "My Stream")
        val e = store.entries.first().single()
        assertEquals(Form.RADIO, e.form)
        assertEquals(40, e.id.length) // SHA-1 hex
        assertEquals("My Stream", e.title)
        assertEquals("https://live/manual.mp3", e.url)
    }

    @Test fun `inCatalogUuids returns ids of existing RADIO entries`() = runTest {
        val store = store()
        RadioCatalog.addStation(store, station("u1", "A"), imagePath = null)
        RadioCatalog.addStation(store, station("u2", "B"), imagePath = null)
        val ids = RadioCatalog.inCatalogUuids(store.entries.first())
        assertEquals(setOf("u1", "u2"), ids)
    }
}
