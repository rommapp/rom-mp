package com.rommapp.rommp.data.remote

import com.rommapp.rommp.data.remote.dto.PaginatedResponse
import com.rommapp.rommp.data.remote.dto.RomSummaryDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** The general ROMs list, used here to browse games that have soundtracks. */
interface RommRomsApi {

    @GET("api/roms")
    suspend fun getRoms(
        @Query("has_soundtrack") hasSoundtrack: Boolean? = null,
        @Query("search_term") searchTerm: String? = null,
        @Query("order_by") orderBy: String = "name",
        @Query("order_dir") orderDir: String = "asc",
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
    ): PaginatedResponse<RomSummaryDto>

    /** A single game — used for its cover art (path_cover_large). */
    @GET("api/roms/{id}/simple")
    suspend fun getRom(@Path("id") id: Int): RomSummaryDto
}
