package com.rommapp.rommp.data.remote

import com.rommapp.rommp.data.remote.dto.FacetValueDto
import com.rommapp.rommp.data.remote.dto.MusicTrackDto
import com.rommapp.rommp.data.remote.dto.PaginatedResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * RomM's music-first API (rommapp/romm PR #3640). All routes are roms.read-scoped
 * and require the Bearer token; see docs/music-api-integration.md.
 */
interface RommMusicApi {

    @GET("api/music/tracks")
    suspend fun getTracks(
        @Query("search") search: String? = null,
        @Query("artist") artist: String? = null,
        @Query("album") album: String? = null,
        @Query("genre") genre: String? = null,
        @Query("platform_ids") platformIds: List<Int>? = null,
        @Query("rom_id") romId: Int? = null,
        @Query("year") year: Int? = null,
        @Query("min_duration") minDuration: Double? = null,
        @Query("max_duration") maxDuration: Double? = null,
        @Query("order_by") orderBy: String = "title",
        @Query("order_dir") orderDir: String = "asc",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PaginatedResponse<MusicTrackDto>

    @GET("api/music/artists")
    suspend fun getArtists(
        @Query("search") search: String? = null,
        @Query("platform_ids") platformIds: List<Int>? = null,
        @Query("order_by") orderBy: String = "count",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PaginatedResponse<FacetValueDto>

    @GET("api/music/albums")
    suspend fun getAlbums(
        @Query("search") search: String? = null,
        @Query("platform_ids") platformIds: List<Int>? = null,
        @Query("order_by") orderBy: String = "count",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PaginatedResponse<FacetValueDto>

    @GET("api/music/genres")
    suspend fun getGenres(
        @Query("search") search: String? = null,
        @Query("platform_ids") platformIds: List<Int>? = null,
        @Query("order_by") orderBy: String = "count",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PaginatedResponse<FacetValueDto>
}
