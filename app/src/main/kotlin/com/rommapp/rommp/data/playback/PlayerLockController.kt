package com.rommapp.rommp.data.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Activity-level key input to the Now Playing lock. Armed only while the
 * player view is on screen ([playerVisible], set by NowPlayingScreen's lifecycle
 * effect); rotate-shut (KEYCODE_F12 down) then engages the same locked state as
 * the player's lock button. Rotate-open (F12 up) releases it again. Off the
 * player, [playerVisible] is false and the Activity ignores F12 so it falls
 * through to the default system keyguard.
 */
@Singleton
class PlayerLockController @Inject constructor() {
    /** True only while NowPlayingScreen is composed; gates F12 handling. */
    val playerVisible = AtomicBoolean(false)

    private val _lockRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val lockRequests: SharedFlow<Unit> = _lockRequests.asSharedFlow()

    private val _unlockRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val unlockRequests: SharedFlow<Unit> = _unlockRequests.asSharedFlow()

    /** Called from the Activity on rotate-shut; engages the player lock. */
    fun requestLock() {
        _lockRequests.tryEmit(Unit)
    }

    /** Called from the Activity on rotate-open; releases the player lock. */
    fun requestUnlock() {
        _unlockRequests.tryEmit(Unit)
    }
}
