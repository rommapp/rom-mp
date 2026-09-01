package com.rommapp.rommp.data.remote

import com.rommapp.rommp.data.remote.dto.CurrentUserDto
import retrofit2.http.GET

/** Identity of the authed user (me.read scope); used to tell own playlists apart. */
interface RommUsersApi {

    @GET("api/users/me")
    suspend fun getCurrentUser(): CurrentUserDto
}
