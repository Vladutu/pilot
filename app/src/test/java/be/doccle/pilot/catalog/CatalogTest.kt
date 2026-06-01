package be.doccle.pilot.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {

    @Test
    fun `catalog has at least three entries`() {
        assertTrue(
            "expected at least 3 entries, got ${CATALOG.size}",
            CATALOG.size >= 3,
        )
    }

    @Test
    fun `catalog has at most five entries`() {
        assertTrue(
            "expected at most 5 entries, got ${CATALOG.size}",
            CATALOG.size <= 5,
        )
    }

    @Test
    fun `every entry has a non-blank label`() {
        for (entry in CATALOG) {
            assertTrue("blank label in $entry", entry.label.isNotBlank())
        }
    }

    @Test
    fun `every entry has a non-blank list id`() {
        for (entry in CATALOG) {
            assertTrue("blank id in $entry", entry.ytListId.isNotBlank())
        }
    }

    @Test
    fun `entry labels are unique`() {
        val labels = CATALOG.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
    }
}
