package com.vladutu.pilot.catalog

import kotlinx.serialization.Serializable

@Serializable
data class CatalogEntry(
    val form: Form,
    val id: String,
    val title: String,
    val imagePath: String?,
    val imageUrl: String? = null,
    val savedAt: Long,
)
