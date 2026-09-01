package com.rommapp.rommp.ui.auth

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rommapp.rommp.data.auth.AuthRepository
import com.rommapp.rommp.data.auth.DeviceAuthPoll
import com.rommapp.rommp.data.auth.PairingChallenge
import com.rommapp.rommp.data.remote.RommApiFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthPhase {
    data object EnterServer : AuthPhase
    data object Connecting : AuthPhase
    data class AwaitingApproval(val challenge: PairingChallenge) : AuthPhase
    data class Error(val message: String) : AuthPhase
}

data class AuthUiState(
    val serverUrl: String = "",
    val phase: AuthPhase = AuthPhase.EnterServer,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun onServerUrlChange(value: String) {
        _state.update { it.copy(serverUrl = value) }
    }

    /**
     * Settles a typed address into the origin the app will actually contact, so a
     * bare host visibly gains its https:// rather than gaining it silently at
     * request time. Safe to call repeatedly; an address that already carries a
     * scheme is left alone.
     */
    fun onServerUrlSettled() {
        _state.update {
            val trimmed = it.serverUrl.trim()
            if (trimmed.isEmpty()) it else it.copy(serverUrl = RommApiFactory.normalizeOrigin(trimmed))
        }
    }

    fun startPairing() {
        pollJob?.cancel()
        if (_state.value.serverUrl.isBlank()) {
            _state.update { it.copy(phase = AuthPhase.Error("Enter a server address")) }
            return
        }
        onServerUrlSettled()
        val url = _state.value.serverUrl
        _state.update { it.copy(phase = AuthPhase.Connecting) }
        pollJob = viewModelScope.launch {
            val challenge = runCatching { authRepository.beginPairing(url) }.getOrElse {
                _state.update { s -> s.copy(phase = AuthPhase.Error(it.message ?: "Could not reach server")) }
                return@launch
            }
            _state.update { it.copy(phase = AuthPhase.AwaitingApproval(challenge)) }
            pollUntilResolved(url, challenge)
        }
    }

    fun cancel() {
        pollJob?.cancel()
        _state.update { it.copy(phase = AuthPhase.EnterServer) }
    }

    private suspend fun pollUntilResolved(url: String, challenge: PairingChallenge) {
        var intervalMs = challenge.intervalSeconds.coerceAtLeast(1) * 1000L
        val deadline = SystemClock.elapsedRealtime() + challenge.expiresInSeconds * 1000L
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(intervalMs)
            when (val result = authRepository.poll(url, challenge.deviceCode)) {
                DeviceAuthPoll.Pending -> Unit
                DeviceAuthPoll.SlowDown -> intervalMs += 5_000L
                is DeviceAuthPoll.Approved -> return // root observes the persisted token
                DeviceAuthPoll.Denied ->
                    return fail("Approval was denied")
                DeviceAuthPoll.Expired ->
                    return fail("The code expired. Try again.")
                is DeviceAuthPoll.Failed ->
                    return fail(result.message)
            }
        }
        fail("The code expired. Try again.")
    }

    private fun fail(message: String) {
        _state.update { it.copy(phase = AuthPhase.Error(message)) }
    }
}
