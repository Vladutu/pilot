package com.vladutu.pilot.radio

import com.vladutu.pilot.catalog.CatalogEntry
import com.vladutu.pilot.catalog.CatalogStore
import com.vladutu.pilot.catalog.Form
import java.security.MessageDigest

/** Maps radio stations into the shared catalog as RADIO entries. */
object RadioCatalog {

    /** Add a discovered station. Id = stationUuid (stable across stream-URL changes). */
    suspend fun addStation(store: CatalogStore, station: RadioStation, imagePath: String?) {
        store.upsert(
            CatalogEntry(
                form = Form.RADIO,
                id = station.stationUuid,
                title = station.name,
                imagePath = imagePath,
                imageUrl = station.faviconUrl,
                url = station.streamUrl,
                savedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Manual fallback: paste a raw stream URL. No stationUuid, so key on sha1(url). */
    suspend fun addManual(store: CatalogStore, streamUrl: String, title: String?) {
        store.upsert(
            CatalogEntry(
                form = Form.RADIO,
                id = sha1(streamUrl),
                title = title?.takeIf { it.isNotBlank() } ?: streamUrl,
                imagePath = null,
                imageUrl = null,
                url = streamUrl,
                savedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Station ids already in the catalog — used to mark/disable already-added search results. */
    fun inCatalogUuids(entries: List<CatalogEntry>): Set<String> =
        entries.filter { it.form == Form.RADIO }.map { it.id }.toSet()

    private fun sha1(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
