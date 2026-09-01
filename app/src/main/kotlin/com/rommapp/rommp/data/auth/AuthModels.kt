package com.rommapp.rommp.data.auth

import com.rommapp.rommp.data.remote.dto.DeviceAuthTokenResponse

/** Result of a single poll of the device-token endpoint (RFC 8628 semantics). */
sealed interface DeviceAuthPoll {
    data object Pending : DeviceAuthPoll
    data object SlowDown : DeviceAuthPoll
    data object Denied : DeviceAuthPoll
    data object Expired : DeviceAuthPoll
    data class Approved(val token: DeviceAuthTokenResponse) : DeviceAuthPoll
    data class Failed(val message: String) : DeviceAuthPoll
}

/** Pairing details to display: the QR payload and the human-readable code. */
data class PairingChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val qrContent: String,
    val intervalSeconds: Int,
    val expiresInSeconds: Int,
)
