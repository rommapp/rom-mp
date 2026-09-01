package com.rommapp.rommp.data.remote

import com.rommapp.rommp.data.remote.dto.DeviceAuthInitRequest
import com.rommapp.rommp.data.remote.dto.DeviceAuthInitResponse
import com.rommapp.rommp.data.remote.dto.DeviceAuthTokenRequest
import com.rommapp.rommp.data.remote.dto.DeviceAuthTokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * RomM HTTP surface used by the auth slice. Endpoints are relative to a
 * per-server base URL (see [RommApiFactory]); the device-auth endpoints are
 * unauthenticated, so no token is attached.
 */
interface RommApi {

    @GET("api/heartbeat")
    suspend fun heartbeat(): Response<Map<String, Any?>>

    @POST("api/auth/device/init")
    suspend fun deviceInit(@Body request: DeviceAuthInitRequest): DeviceAuthInitResponse

    /**
     * Polled until approval. Returns 200 with the token on approval, or 400 with
     * an OAuth detail string (authorization_pending / slow_down / access_denied /
     * expired_token) while pending; the caller inspects [Response] for both.
     */
    @POST("api/auth/device/token")
    suspend fun deviceToken(@Body request: DeviceAuthTokenRequest): Response<DeviceAuthTokenResponse>
}
