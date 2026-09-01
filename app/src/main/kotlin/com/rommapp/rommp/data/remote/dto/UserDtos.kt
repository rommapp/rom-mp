package com.rommapp.rommp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** The authed user from GET /api/users/me; only what playlist ownership needs. */
@JsonClass(generateAdapter = true)
data class CurrentUserDto(
    @Json(name = "id") val id: Int,
    @Json(name = "username") val username: String,
)
