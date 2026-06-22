package com.vladutu.pilot.radio

import org.junit.Assert.assertEquals
import org.junit.Test

class RadioCountryTest {

    @Test fun `maps name code and station count`() {
        val json = """[{"name":"Romania","iso_3166_1":"RO","stationcount":234}]"""
        val countries = RadioCountry.listFrom(json)
        assertEquals(1, countries.size)
        assertEquals(RadioCountry(code = "RO", name = "Romania", stationCount = 234), countries[0])
    }

    @Test fun `sorts by station count descending`() {
        val json = """
            [
              {"name":"Romania","iso_3166_1":"RO","stationcount":234},
              {"name":"Germany","iso_3166_1":"DE","stationcount":4000},
              {"name":"Moldova","iso_3166_1":"MD","stationcount":40}
            ]
        """.trimIndent()
        val codes = RadioCountry.listFrom(json).map { it.code }
        assertEquals(listOf("DE", "RO", "MD"), codes)
    }

    @Test fun `drops entries with blank name or code`() {
        val json = """
            [
              {"name":"Romania","iso_3166_1":"RO","stationcount":234},
              {"name":"","iso_3166_1":"XX","stationcount":5},
              {"name":"NoCode","iso_3166_1":"","stationcount":5}
            ]
        """.trimIndent()
        val countries = RadioCountry.listFrom(json)
        assertEquals(1, countries.size)
        assertEquals("RO", countries[0].code)
    }
}
