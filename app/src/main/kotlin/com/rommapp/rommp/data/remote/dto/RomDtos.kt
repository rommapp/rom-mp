package com.rommapp.rommp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** A game from GET /api/roms (fields relevant to the music library shelf). */
@JsonClass(generateAdapter = true)
data class RomSummaryDto(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String? = null,
    @Json(name = "platform_slug") val platformSlug: String? = null,
    @Json(name = "platform_display_name") val platformName: String? = null,
    @Json(name = "path_cover_small") val pathCoverSmall: String? = null,
    @Json(name = "path_cover_large") val pathCoverLarge: String? = null,
    @Json(name = "url_cover") val urlCover: String? = null,
    @Json(name = "has_soundtrack") val hasSoundtrack: Boolean = false,
)
