package com.vladutu.pilot.catalog

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CatalogStoreTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: CatalogStore
    private val scope = TestScope(UnconfinedTestDispatcher())
    private var fakeNow: Long = 0L

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "catalog.preferences_pb") },
        )
        store = CatalogStore(dataStore, clock = { fakeNow })
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun entry(form: Form, id: String, title: String, savedAt: Long, imagePath: String? = null) =
        CatalogEntry(form = form, id = id, title = title, imagePath = imagePath, savedAt = savedAt)

    @Test
    fun `entries is empty initially`() = runTest {
        assertEquals(emptyList<CatalogEntry>(), store.entries.first())
    }

    @Test
    fun `upsert adds new entry`() = runTest {
        val e = entry(Form.PLAYLIST, "PLabc", "Bachata", savedAt = 100L)
        store.upsert(e)
        assertEquals(listOf(e), store.entries.first())
    }

    @Test
    fun `upsert dedupes by form and id, refreshing savedAt`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "PLabc", "Bachata", savedAt = 100L))
        store.upsert(entry(Form.PLAYLIST, "PLabc", "Bachata", savedAt = 200L))
        val all = store.entries.first()
        assertEquals(1, all.size)
        assertEquals(200L, all.first().savedAt)
    }

    @Test
    fun `entries sorted by savedAt descending`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "old", "old", savedAt = 100L))
        store.upsert(entry(Form.PLAYLIST, "new", "new", savedAt = 300L))
        store.upsert(entry(Form.PLAYLIST, "mid", "mid", savedAt = 200L))
        val ids = store.entries.first().map { it.id }
        assertEquals(listOf("new", "mid", "old"), ids)
    }

    @Test
    fun `entries with same form but different id coexist`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "a", "A", savedAt = 100L))
        store.upsert(entry(Form.PLAYLIST, "b", "B", savedAt = 200L))
        assertEquals(2, store.entries.first().size)
    }

    @Test
    fun `entries with same id but different form coexist`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "x", "Plist", savedAt = 100L))
        store.upsert(entry(Form.SONG, "x", "Song", savedAt = 200L))
        assertEquals(2, store.entries.first().size)
    }

    @Test
    fun `updateMeta only touches matching entry`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "a", "old-a", savedAt = 100L, imagePath = null))
        store.upsert(entry(Form.PLAYLIST, "b", "old-b", savedAt = 200L, imagePath = null))

        store.updateMeta(Form.PLAYLIST, "a", title = "new-a", imagePath = "/cache/a.jpg", imageUrl = null)

        val byId = store.entries.first().associateBy { it.id }
        assertEquals("new-a", byId["a"]!!.title)
        assertEquals("/cache/a.jpg", byId["a"]!!.imagePath)
        assertEquals("old-b", byId["b"]!!.title)
        assertNull(byId["b"]!!.imagePath)
    }

    @Test
    fun `updateMeta on missing entry is a no-op`() = runTest {
        store.updateMeta(Form.SONG, "missing", title = "x", imagePath = null, imageUrl = null)
        assertEquals(emptyList<CatalogEntry>(), store.entries.first())
    }

    @Test
    fun `rename changes title without touching imagePath or savedAt`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "a", "old", savedAt = 100L, imagePath = "/cache/a.jpg"))
        store.rename(Form.PLAYLIST, "a", "new")
        val e = store.entries.first().single()
        assertEquals("new", e.title)
        assertEquals("/cache/a.jpg", e.imagePath)
        assertEquals(100L, e.savedAt)
    }

    @Test
    fun `touch bumps savedAt and re-sorts the entry to the top`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "old", "old", savedAt = 100L))
        store.upsert(entry(Form.PLAYLIST, "mid", "mid", savedAt = 200L))
        store.upsert(entry(Form.PLAYLIST, "new", "new", savedAt = 300L))

        fakeNow = 500L
        store.touch(Form.PLAYLIST, "old")

        val all = store.entries.first()
        assertEquals(listOf("old", "new", "mid"), all.map { it.id })
        assertEquals(500L, all.first().savedAt)
    }

    @Test
    fun `touch on missing entry is a no-op`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "a", "A", savedAt = 100L))
        fakeNow = 500L
        store.touch(Form.SONG, "a") // wrong form
        store.touch(Form.PLAYLIST, "missing")

        val e = store.entries.first().single()
        assertEquals(100L, e.savedAt)
    }

    @Test
    fun `touch only affects the matching form-id pair`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "x", "Plist", savedAt = 100L))
        store.upsert(entry(Form.SONG, "x", "Song", savedAt = 200L))

        fakeNow = 500L
        store.touch(Form.PLAYLIST, "x")

        val byForm = store.entries.first().associateBy { it.form }
        assertEquals(500L, byForm[Form.PLAYLIST]!!.savedAt)
        assertEquals(200L, byForm[Form.SONG]!!.savedAt)
    }

    @Test
    fun `delete removes matching entry`() = runTest {
        store.upsert(entry(Form.PLAYLIST, "a", "A", savedAt = 100L))
        store.upsert(entry(Form.PLAYLIST, "b", "B", savedAt = 200L))
        store.delete(Form.PLAYLIST, "a")
        val ids = store.entries.first().map { it.id }
        assertEquals(listOf("b"), ids)
    }

    @Test
    fun `entries survive serialization round-trip when reopening the store`() = runTest {
        store.upsert(entry(Form.SONG, "vid1", "First Song", savedAt = 500L, imagePath = null))
        // Build a fresh CatalogStore over the same underlying DataStore file.
        val reopened = CatalogStore(dataStore)
        val all = reopened.entries.first()
        assertEquals(1, all.size)
        assertEquals("First Song", all.first().title)
        assertNull(all.first().imagePath)
    }
}
