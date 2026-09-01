package com.rommapp.rommp.data.remote

import com.rommapp.rommp.data.remote.dto.MusicTrackDto
import com.rommapp.rommp.data.remote.dto.PaginatedResponse
import com.rommapp.rommp.data.remote.dto.PlaylistCreateRequest
import com.rommapp.rommp.data.remote.dto.PlaylistDto
import com.rommapp.rommp.data.remote.dto.PlaylistTrackIdsRequest
import com.rommapp.rommp.data.remote.dto.PlaylistTracksAddedResponse
import com.rommapp.rommp.data.remote.dto.PlaylistTracksRemovedResponse
import com.rommapp.rommp.data.remote.dto.PlaylistUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * RomM's music playlists API. Reads need the playlists.read scope, writes
 * playlists.write; write routes 403 on playlists the user does not own.
 */
interface RommPlaylistsApi {

    /** The user's playlists plus other users' public ones. */
    @GET("api/music/playlists")
    suspend fun getPlaylists(): List<PlaylistDto>

    /** 400 when the user already has a playlist with that name. */
    @POST("api/music/playlists")
    suspend fun createPlaylist(@Body request: PlaylistCreateRequest): PlaylistDto

    @GET("api/music/playlists/{id}")
    suspend fun getPlaylist(@Path("id") id: Int): PlaylistDto

    @PUT("api/music/playlists/{id}")
    suspend fun updatePlaylist(
        @Path("id") id: Int,
        @Body request: PlaylistUpdateRequest,
    ): PlaylistDto

    @DELETE("api/music/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Int)

    /**
     * Resolvable tracks in playlist order. Entries whose file is gone are
     * omitted, so the page total can be lower than the playlist's track_count.
     */
    @GET("api/music/playlists/{id}/tracks")
    suspend fun getPlaylistTracks(
        @Path("id") id: Int,
        @Query("order_by") orderBy: String = "position",
        @Query("order_dir") orderDir: String = "asc",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PaginatedResponse<MusicTrackDto>

    /** Appends; tracks already present are ignored. */
    @POST("api/music/playlists/{id}/tracks")
    suspend fun addTracks(
        @Path("id") id: Int,
        @Body request: PlaylistTrackIdsRequest,
    ): PlaylistTracksAddedResponse

    @HTTP(method = "DELETE", path = "api/music/playlists/{id}/tracks", hasBody = true)
    suspend fun removeTracks(
        @Path("id") id: Int,
        @Body request: PlaylistTrackIdsRequest,
    ): PlaylistTracksRemovedResponse

    /** Full desired order; entries not listed keep their relative order after. */
    @PUT("api/music/playlists/{id}/tracks/order")
    suspend fun setTrackOrder(
        @Path("id") id: Int,
        @Body request: PlaylistTrackIdsRequest,
    )
}
