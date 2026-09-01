package com.rommapp.rommp.data.auth

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.rommapp.rommp.data.remote.RommApi
import com.rommapp.rommp.data.remote.RommApiFactory
import com.rommapp.rommp.data.remote.dto.ApiErrorDetail
import com.rommapp.rommp.data.remote.dto.DeviceAuthInitRequest
import com.rommapp.rommp.data.remote.dto.DeviceAuthTokenRequest
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the RomM device-authorization flow: start pairing (the handheld shows
 * a QR), poll for approval, and persist the resulting rmm_ client token.
 */
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiFactory: RommApiFactory,
    private val tokenStore: TokenStore,
    moshi: Moshi,
) {
    private val errorAdapter = moshi.adapter(ApiErrorDetail::class.java)

    val isAuthenticated: Flow<Boolean> = tokenStore.isAuthenticated

    /** Starts pairing against [baseUrl]; returns what to render as the QR + code. */
    suspend fun beginPairing(baseUrl: String): PairingChallenge {
        val api = apiFactory.create(baseUrl)
        val response = api.deviceInit(
            DeviceAuthInitRequest(
                clientDeviceIdentifier = clientDeviceIdentifier(),
                name = deviceName(),
                client = CLIENT_NAME,
                platform = "android",
                clientVersion = null,
                requestedScopes = REQUESTED_SCOPES,
            ),
        )
        val origin = RommApiFactory.normalizeOrigin(baseUrl)
        return PairingChallenge(
            deviceCode = response.deviceCode,
            userCode = response.userCode,
            verificationUrl = origin + response.verificationPathComplete,
            qrContent = origin + response.verificationPathComplete,
            intervalSeconds = response.interval,
            expiresInSeconds = response.expiresIn,
        )
    }

    /** One poll of the token endpoint; persists credentials on approval. */
    suspend fun poll(baseUrl: String, deviceCode: String): DeviceAuthPoll {
        val api = apiFactory.create(baseUrl)
        return runCatching { pollOnce(api, baseUrl, deviceCode) }
            .getOrElse { DeviceAuthPoll.Failed(it.message ?: "Network error") }
    }

    private suspend fun pollOnce(api: RommApi, baseUrl: String, deviceCode: String): DeviceAuthPoll {
        val response = api.deviceToken(DeviceAuthTokenRequest(deviceCode))
        val body = response.body()
        if (response.isSuccessful && body != null) {
            tokenStore.save(
                baseUrl = RommApiFactory.normalizeOrigin(baseUrl),
                token = body.accessToken,
                deviceId = body.deviceId,
                scopes = body.scopes,
            )
            return DeviceAuthPoll.Approved(body)
        }
        val detail = runCatching {
            response.errorBody()?.string()?.let { errorAdapter.fromJson(it)?.detail }
        }.getOrNull()
        return when (detail) {
            "authorization_pending" -> DeviceAuthPoll.Pending
            "slow_down" -> DeviceAuthPoll.SlowDown
            "access_denied" -> DeviceAuthPoll.Denied
            "expired_token" -> DeviceAuthPoll.Expired
            else -> DeviceAuthPoll.Failed(detail ?: "HTTP ${response.code()}")
        }
    }

    suspend fun signOut() = tokenStore.clear()

    private fun clientDeviceIdentifier(): String {
        @Suppress("HardwareIds")
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty().ifBlank { "unknown" }
        return "$CLIENT_NAME-$androidId"
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private companion object {
        const val CLIENT_NAME = "rommp"
        val REQUESTED_SCOPES = listOf(
            "me.read",
            "platforms.read",
            "roms.read",
            "collections.read",
            "assets.read",
            "playlists.read",
            "playlists.write",
        )
    }
}
