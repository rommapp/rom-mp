package com.rommapp.rommp.data.remote

import com.rommapp.rommp.data.remote.dto.FavoriteIdsRequest
import com.rommapp.rommp.data.remote.dto.FavoritesAddedResponse
import com.rommapp.rommp.data.remote.dto.FavoritesRemovedResponse
import com.rommapp.rommp.data.remote.dto.MusicTrackDto
import com.rommapp.rommp.data.remote.dto.PaginatedResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * RomM's music favorites API. Reads need the playlists.read scope, writes
 * playlists.write; both are only granted on tokens paired after those scopes
 * were added, so callers gate on [com.rommapp.rommp.data.auth.TokenStore.hasScope].
 */
interface RommFavoritesApi {

    @GET("api/music/favorites")
    suspend fun getFavorites(
        @Query("order_by") orderBy: String = "title",
        @Query("order_dir") orderDir: String = "asc",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): PaginatedResponse<MusicTrackDto>

    @POST("api/music/favorites")
    suspend fun addFavorites(@Body request: FavoriteIdsRequest): FavoritesAddedResponse

    @HTTP(method = "DELETE", path = "api/music/favorites", hasBody = true)
    suspend fun removeFavorites(@Body request: FavoriteIdsRequest): FavoritesRemovedResponse
}
