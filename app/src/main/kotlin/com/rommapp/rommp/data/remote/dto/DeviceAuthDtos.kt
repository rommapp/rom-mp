package com.rommapp.rommp.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Request body for POST /api/auth/device/init. The handheld (the "client")
 * starts the OAuth device-authorization flow; the user approves on their phone.
 */
@JsonClass(generateAdapter = true)
data class DeviceAuthInitRequest(
    @Json(name = "client_device_identifier") val clientDeviceIdentifier: String,
    @Json(name = "name") val name: String,
    @Json(name = "client") val client: String,
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "client_version") val clientVersion: String? = null,
    @Json(name = "requested_scopes") val requestedScopes: List<String>,
)

@JsonClass(generateAdapter = true)
data class DeviceAuthInitResponse(
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_path") val verificationPath: String,
    @Json(name = "verification_path_complete") val verificationPathComplete: String,
    @Json(name = "expires_in") val expiresIn: Int,
    @Json(name = "interval") val interval: Int,
)

@JsonClass(generateAdapter = true)
data class DeviceAuthTokenRequest(
    @Json(name = "device_code") val deviceCode: String,
)

@JsonClass(generateAdapter = true)
data class DeviceAuthTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "scopes") val scopes: List<String> = emptyList(),
    @Json(name = "expires_at") val expiresAt: String? = null,
)

/** RomM returns HTTP 400 with an OAuth-style {"detail": "..."} while pending. */
@JsonClass(generateAdapter = true)
data class ApiErrorDetail(
    @Json(name = "detail") val detail: String? = null,
)
