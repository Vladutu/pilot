package com.vladutu.pilot.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsStoreTest {

    @get:Rule
    val tmp: TemporaryFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: SettingsStore
    private val scope = TestScope(UnconfinedTestDispatcher())

    private val topic = "copilot-689e337645dc256a2b03d210d7b3c41b"

    @Before fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
        store = SettingsStore(dataStore)
    }

    @Test fun `topic is null before pairing`() = runTest {
        assertNull(store.topicFlow.first())
    }

    @Test fun `setTopic then read returns the topic`() = runTest {
        store.setTopic(topic)
        assertEquals(topic, store.topicFlow.first())
    }

    @Test fun `setTopic overwrites a previous topic (re-pair)`() = runTest {
        val other = "copilot-00000000000000000000000000000000"
        store.setTopic(topic)
        store.setTopic(other)
        assertEquals(other, store.topicFlow.first())
    }
}
