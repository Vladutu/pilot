package com.vladutu.pilot.catalog

import org.junit.Assert.assertEquals
import org.junit.Test

class FormTest {
    @Test fun `wire values`() {
        assertEquals("playlist", Form.PLAYLIST.wire)
        assertEquals("song", Form.SONG.wire)
        assertEquals("destination", Form.DESTINATION.wire)
        assertEquals("radio", Form.RADIO.wire)
    }
}
