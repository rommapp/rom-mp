package com.rommapp.rommp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** A playlist from /api/music/playlists (own + other users' public). */
@JsonClass(generateAdapter = true)
data class PlaylistDto(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "is_public") val isPublic: Boolean = false,
    @Json(name = "user_id") val userId: Int,
    @Json(name = "owner_username") val ownerUsername: String,
    @Json(name = "track_count") val trackCount: Int = 0,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
)

@JsonClass(generateAdapter = true)
data class PlaylistCreateRequest(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "is_public") val isPublic: Boolean = false,
)

/** PUT body; omitted (null) fields are left unchanged server-side. */
@JsonClass(generateAdapter = true)
data class PlaylistUpdateRequest(
    @Json(name = "name") val name: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "is_public") val isPublic: Boolean? = null,
)

/** Body for playlist track membership ops and PUT tracks/order. */
@JsonClass(generateAdapter = true)
data class PlaylistTrackIdsRequest(
    @Json(name = "rom_file_ids") val romFileIds: List<Int>,
)

@JsonClass(generateAdapter = true)
data class PlaylistTracksAddedResponse(
    @Json(name = "added") val added: Int = 0,
)

@JsonClass(generateAdapter = true)
data class PlaylistTracksRemovedResponse(
    @Json(name = "removed") val removed: Int = 0,
)
