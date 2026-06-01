package com.vladutu.pilot.catalog

data class CatalogEntry(
    val label: String,
    val ytListId: String,
)

val CATALOG: List<CatalogEntry> = listOf(
    CatalogEntry(label = "Bachata", ytListId = "PLtQnabPiV7-c43mBxlEBnKDXQDqsHCPtx"),
    CatalogEntry(label = "Kizomba", ytListId = "PLtQnabPiV7-eeFBOJjFexpeImIXASsZjH"),
)
