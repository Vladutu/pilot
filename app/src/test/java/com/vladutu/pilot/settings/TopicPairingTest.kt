package com.vladutu.pilot.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopicPairingTest {

    private val good = "copilot-689e337645dc256a2b03d210d7b3c41b"

    // --- validate ---

    @Test fun `validate accepts a well-formed topic`() {
        assertEquals(good, TopicPairing.validate(good))
    }

    @Test fun `validate trims surrounding whitespace`() {
        assertEquals(good, TopicPairing.validate("  $good\n"))
    }

    @Test fun `validate rejects wrong prefix`() {
        assertNull(TopicPairing.validate("pilot-689e337645dc256a2b03d210d7b3c41b"))
    }

    @Test fun `validate rejects uppercase hex`() {
        assertNull(TopicPairing.validate("copilot-689E337645DC256A2B03D210D7B3C41B"))
    }

    @Test fun `validate rejects wrong length`() {
        assertNull(TopicPairing.validate("copilot-689e337645dc256a2b03d210d7b3c41"))   // 31
        assertNull(TopicPairing.validate("copilot-689e337645dc256a2b03d210d7b3c41bb")) // 33
    }

    @Test fun `validate rejects non-hex characters`() {
        assertNull(TopicPairing.validate("copilot-689e337645dc256a2b03d210d7b3c41z"))
    }

    @Test fun `validate rejects null and blank`() {
        assertNull(TopicPairing.validate(null))
        assertNull(TopicPairing.validate(""))
        assertNull(TopicPairing.validate("   "))
    }

    // --- parsePairUri ---

    @Test fun `parsePairUri extracts and validates the topic`() {
        assertEquals(good, TopicPairing.parsePairUri("pilot://pair?topic=$good"))
    }

    @Test fun `parsePairUri tolerates extra query params and ordering`() {
        assertEquals(good, TopicPairing.parsePairUri("pilot://pair?v=1&topic=$good&x=y"))
    }

    @Test fun `parsePairUri trims whitespace around the uri`() {
        assertEquals(good, TopicPairing.parsePairUri("  pilot://pair?topic=$good  "))
    }

    @Test fun `parsePairUri rejects wrong scheme`() {
        assertNull(TopicPairing.parsePairUri("https://pair?topic=$good"))
    }

    @Test fun `parsePairUri rejects wrong host`() {
        assertNull(TopicPairing.parsePairUri("pilot://connect?topic=$good"))
    }

    @Test fun `parsePairUri rejects missing topic param`() {
        assertNull(TopicPairing.parsePairUri("pilot://pair?foo=bar"))
    }

    @Test fun `parsePairUri rejects an invalid topic inside a valid-looking uri`() {
        assertNull(TopicPairing.parsePairUri("pilot://pair?topic=not-a-topic"))
    }

    @Test fun `parsePairUri rejects a raw topic that is not a uri`() {
        assertNull(TopicPairing.parsePairUri(good))
    }

    @Test fun `parsePairUri rejects null`() {
        assertNull(TopicPairing.parsePairUri(null))
    }
}
