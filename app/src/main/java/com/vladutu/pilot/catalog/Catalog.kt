package com.vladutu.pilot.catalog

data class CatalogEntry(
    val label: String,
    val ytListId: String,
)

// Replace the placeholder ids with real YouTube Music playlist ids.
// To get one: open a playlist in YT Music → Share → Copy link → extract
// the part after `list=`. Strip any trailing `&si=...` tracking suffix.
val CATALOG: List<CatalogEntry> = listOf(
    CatalogEntry(label = "Driving rock",   ytListId = "PLREPLACE_WITH_REAL_ID_1"),
    CatalogEntry(label = "Chill jazz",     ytListId = "PLREPLACE_WITH_REAL_ID_2"),
    CatalogEntry(label = "Morning coffee", ytListId = "PLREPLACE_WITH_REAL_ID_3"),
)
