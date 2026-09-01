package com.rommapp.rommp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** RomM's standard limit/offset pagination envelope. */
@JsonClass(generateAdapter = true)
data class PaginatedResponse<T>(
    @Json(name = "items") val items: List<T> = emptyList(),
    @Json(name = "total") val total: Int = 0,
    @Json(name = "limit") val limit: Int = 0,
    @Json(name = "offset") val offset: Int = 0,
)

/** A soundtrack track from GET /api/music/tracks. */
@JsonClass(generateAdapter = true)
data class MusicTrackDto(
    @Json(name = "rom_file_id") val romFileId: Int,
    @Json(name = "rom_id") val romId: Int,
    @Json(name = "title") val title: String? = null,
    @Json(name = "artist") val artist: String? = null,
    @Json(name = "album") val album: String? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "year") val year: Int? = null,
    @Json(name = "track") val track: Int? = null,
    @Json(name = "disc") val disc: Int? = null,
    @Json(name = "duration_seconds") val durationSeconds: Double? = null,
    @Json(name = "has_embedded_cover") val hasEmbeddedCover: Boolean = false,
    @Json(name = "is_favorite") val isFavorite: Boolean = false,
    @Json(name = "game_name") val gameName: String? = null,
    @Json(name = "platform_id") val platformId: Int,
    @Json(name = "platform_slug") val platformSlug: String,
    @Json(name = "platform_name") val platformName: String,
    @Json(name = "stream_url") val streamUrl: String,
    @Json(name = "cover_url") val coverUrl: String? = null,
)

/** A distinct value + count from the facet endpoints (artists/albums/genres). */
@JsonClass(generateAdapter = true)
data class FacetValueDto(
    @Json(name = "value") val value: String,
    @Json(name = "count") val count: Int,
)

/** Body for POST/DELETE /api/music/favorites. */
@JsonClass(generateAdapter = true)
data class FavoriteIdsRequest(
    @Json(name = "rom_file_ids") val romFileIds: List<Int>,
)

@JsonClass(generateAdapter = true)
data class FavoritesAddedResponse(
    @Json(name = "added") val added: Int = 0,
)

@JsonClass(generateAdapter = true)
data class FavoritesRemovedResponse(
    @Json(name = "removed") val removed: Int = 0,
)
