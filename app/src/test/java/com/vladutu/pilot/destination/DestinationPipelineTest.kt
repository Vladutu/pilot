package com.vladutu.pilot.destination

import com.vladutu.pilot.catalog.CatalogEntry
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import com.vladutu.pilot.net.NtfyPublishException
import com.vladutu.pilot.net.NtfyPublisher
import com.vladutu.pilot.share.MapsToWazeConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DestinationPipelineTest {

    private lateinit var converterServer: MockWebServer
    private val savedEntries = mutableListOf<CatalogEntry>()
    private lateinit var publisher: FakePublisher

    private val wazeUrl = "https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes"

    @Before
    fun setUp() {
        converterServer = MockWebServer().apply { start() }
        savedEntries.clear()
        publisher = FakePublisher()
    }

    @After
    fun tearDown() {
        converterServer.shutdown()
    }

    private fun newPipeline(): DestinationPipeline = DestinationPipeline(
        converter = MapsToWazeConverter(OkHttpClient(), converterServer.url("/").toString()),
        catalogStore = FakeCatalogStore(savedEntries),
        publisher = publisher,
        clock = { 1_700_000_000_000L },
    )

    @Test
    fun ingest_mapsUrl_convertsSavesAndPublishes() = runBlocking {
        converterServer.enqueue(MockResponse().setResponseCode(302).setHeader("Location", wazeUrl))

        val result = newPipeline().ingest(
            urlText = "https://www.google.com/maps/place/Brandenburg+Gate/@52.5,13.4,17z/",
            manualTitle = null,
            subject = "Brandenburg Gate",
        )

        assertTrue("expected Success, got $result", result is IngestResult.Success)
        assertEquals("Brandenburg Gate", (result as IngestResult.Success).title)
        assertEquals(1, savedEntries.size)
        assertEquals(Form.DESTINATION, savedEntries[0].form)
        assertEquals(wazeUrl, savedEntries[0].id)
        assertEquals("Brandenburg Gate", savedEntries[0].title)
        assertEquals(listOf(wazeUrl), publisher.publishedWaze)
    }

    @Test
    fun ingest_wazeUrl_normalizesSavesAndPublishes() = runBlocking {
        val pasted = "https://ul.waze.com/ul?ll=52.5,13.4"

        val result = newPipeline().ingest(
            urlText = pasted,
            manualTitle = "Home",
            subject = null,
        )

        assertTrue(result is IngestResult.Success)
        assertEquals("Home", (result as IngestResult.Success).title)
        assertEquals(1, savedEntries.size)
        assertEquals("https://ul.waze.com/ul?ll=52.5,13.4&navigate=yes", savedEntries[0].id)
        assertEquals("Home", savedEntries[0].title)
    }

    @Test
    fun ingest_unrecognizedUrl_returnsNotARecognizedLink() = runBlocking {
        val result = newPipeline().ingest(urlText = "https://example.com/foo", manualTitle = null, subject = null)
        assertEquals(IngestResult.NotARecognizedLink, result)
        assertEquals(0, savedEntries.size)
    }

    @Test
    fun ingest_httpWazeUrl_returnsUnsupportedWazeHost() = runBlocking {
        val result = newPipeline().ingest(
            urlText = "http://ul.waze.com/ul?ll=1,2",
            manualTitle = null,
            subject = null,
        )
        // Classifier matches http:// URLs and resolves the host to a WAZE_HOST → WazeShare.
        // WazeUrlNormalizer.normalize() then throws because ALLOWED_PREFIXES are https-only.
        assertEquals(IngestResult.UnsupportedWazeHost, result)
        assertEquals(0, savedEntries.size)
    }

    @Test
    fun ingest_converterReturnsErrorPage_returnsConversionFailed() = runBlocking {
        converterServer.enqueue(MockResponse().setResponseCode(200).setBody("<html>oops</html>"))

        val result = newPipeline().ingest(
            urlText = "https://www.google.com/maps/place/X",
            manualTitle = null,
            subject = null,
        )

        assertEquals(IngestResult.ConversionFailed, result)
        assertEquals(0, savedEntries.size)
        assertTrue(publisher.publishedWaze.isEmpty())
    }

    @Test
    fun ingest_publishFails_entryStillSaved() = runBlocking {
        converterServer.enqueue(MockResponse().setResponseCode(302).setHeader("Location", wazeUrl))
        publisher.failNextPublish = true

        val result = newPipeline().ingest(
            urlText = "https://www.google.com/maps/place/X",
            manualTitle = "Park",
            subject = null,
        )

        assertTrue(result is IngestResult.PublishFailed)
        assertEquals("Park", (result as IngestResult.PublishFailed).title)
        assertEquals(1, savedEntries.size)
    }

    @Test
    fun ingest_manualTitleOverridesSubject() = runBlocking {
        converterServer.enqueue(MockResponse().setResponseCode(302).setHeader("Location", wazeUrl))

        val result = newPipeline().ingest(
            urlText = "https://www.google.com/maps/place/X",
            manualTitle = "Home",
            subject = "Whatever",
        )

        assertEquals("Home", (result as IngestResult.Success).title)
        assertEquals("Home", savedEntries[0].title)
    }

    @Test
    fun ingest_blankManualTitle_fallsBackToSubject() = runBlocking {
        converterServer.enqueue(MockResponse().setResponseCode(302).setHeader("Location", wazeUrl))

        val result = newPipeline().ingest(
            urlText = "https://www.google.com/maps/place/X",
            manualTitle = "   ",
            subject = "From subject",
        )

        assertEquals("From subject", (result as IngestResult.Success).title)
    }

    @Test
    fun ingest_noTitleAnywhere_fallsBackToDestinationPlusSuffix() = runBlocking {
        converterServer.enqueue(MockResponse().setResponseCode(302).setHeader("Location", wazeUrl))

        val result = newPipeline().ingest(
            urlText = "https://maps.app.goo.gl/abc",
            manualTitle = null,
            subject = null,
        )

        val title = (result as IngestResult.Success).title
        assertTrue("got: $title", title.startsWith("Destination "))
        assertEquals(savedEntries[0].title, title)
    }

    private class FakeCatalogStore(val backing: MutableList<CatalogEntry>) : CatalogStore(FAKE_DATASTORE) {
        override suspend fun upsert(entry: CatalogEntry) {
            backing.removeAll { it.form == entry.form && it.id == entry.id }
            backing.add(entry)
        }

        companion object {
            val FAKE_DATASTORE = object : androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
                override val data = MutableStateFlow(androidx.datastore.preferences.core.emptyPreferences())
                override suspend fun updateData(
                    transform: suspend (androidx.datastore.preferences.core.Preferences) -> androidx.datastore.preferences.core.Preferences,
                ) = androidx.datastore.preferences.core.emptyPreferences()
            }
        }
    }

    private class FakePublisher : NtfyPublisher(
        client = OkHttpClient(),
        base = "http://fake",
        topic = "fake",
    ) {
        val publishedWaze = mutableListOf<String>()
        var failNextPublish = false

        override suspend fun publishWaze(url: String) {
            if (failNextPublish) {
                failNextPublish = false
                throw NtfyPublishException("simulated publish failure")
            }
            publishedWaze.add(url)
        }
    }
}
