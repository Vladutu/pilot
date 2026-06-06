package com.vladutu.pilot.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioStationTest {

    @Test fun `maps radio-browser json fields`() {
        val json = """
            [
              {
                "stationuuid": "uuid-1",
                "name": "Europa FM",
                "url": "http://old/stream",
                "url_resolved": "https://live.example.ro/europafm.mp3",
                "favicon": "https://example.ro/fav.png",
                "codec": "MP3",
                "bitrate": 128,
                "lastcheckok": 1
              }
            ]
        """.trimIndent()
        val stations = RadioStation.listFrom(json)
        assertEquals(1, stations.size)
        val s = stations[0]
        assertEquals("uuid-1", s.stationUuid)
        assertEquals("Europa FM", s.name)
        assertEquals("https://live.example.ro/europafm.mp3", s.streamUrl) // url_resolved, not url
        assertEquals("https://example.ro/fav.png", s.faviconUrl)
        assertEquals("MP3", s.codec)
        assertEquals(128, s.bitrate)
        assertTrue(s.lastCheckOk)
    }

    @Test fun `lastcheckok 0 maps to false`() {
        val json = """[{"stationuuid":"u","name":"X","url_resolved":"https://a/s","lastcheckok":0}]"""
        assertEquals(false, RadioStation.listFrom(json)[0].lastCheckOk)
    }

    @Test fun `drops entries with blank url_resolved`() {
        val json = """
            [
              {"stationuuid":"u1","name":"Good","url_resolved":"https://a/s"},
              {"stationuuid":"u2","name":"NoStream","url_resolved":""}
            ]
        """.trimIndent()
        val stations = RadioStation.listFrom(json)
        assertEquals(1, stations.size)
        assertEquals("u1", stations[0].stationUuid)
    }

    @Test fun `blank favicon becomes null`() {
        val json = """[{"stationuuid":"u","name":"X","url_resolved":"https://a/s","favicon":""}]"""
        assertEquals(null, RadioStation.listFrom(json)[0].faviconUrl)
    }
}
