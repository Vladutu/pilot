package com.vladutu.pilot.share

/**
 * Result of resolving a Google Maps URL into a Waze deep link on-device.
 *
 * @property wazeUrl     the `https://ul.waze.com/ul?ll=<lat>,<lng>&navigate=yes` deep link.
 * @property resolvedUrl the final Google Maps URL the short/long link resolved to. Often carries
 *                       a `/place/<Name>/` segment, which the pipeline uses to title the destination.
 */
data class MapsResolution(
    val wazeUrl: String,
    val resolvedUrl: String,
)

/**
 * On-device Google Maps → Waze resolver. Returns null when it cannot produce a Waze link
 * (no coordinates in the resolved URL, or a network failure) so the caller can fall back to the
 * remote [MapsToWazeConverter]. Implementations MUST NOT throw.
 */
interface MapsResolver {
    suspend fun resolve(googleMapsUrl: String): MapsResolution?
}
