package com.vladutu.pilot.discover

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiscoverCategoryStoreTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: DiscoverCategoryStore
    private val scope = TestScope(UnconfinedTestDispatcher())

    @Before fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "discover.preferences_pb") },
        )
        store = DiscoverCategoryStore(dataStore)
    }

    @Test fun `add then read preserves insertion order`() = runTest {
        store.add("Workout")
        store.add("Chill")
        assertEquals(listOf("Workout", "Chill"), store.categories().first())
    }

    @Test fun `add dedupes case-insensitively keeping the first casing`() = runTest {
        store.add("Workout")
        store.add("workout")
        store.add("WORKOUT")
        assertEquals(listOf("Workout"), store.categories().first())
    }

    @Test fun `add trims and ignores blank`() = runTest {
        store.add("  80s rock  ")
        store.add("   ")
        assertEquals(listOf("80s rock"), store.categories().first())
    }

    @Test fun `delete is case-insensitive`() = runTest {
        store.add("Workout")
        store.add("Chill")
        store.delete("WORKOUT")
        assertEquals(listOf("Chill"), store.categories().first())
    }
}
