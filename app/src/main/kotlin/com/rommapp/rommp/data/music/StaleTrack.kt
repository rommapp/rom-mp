package com.rommapp.rommp.data.music

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A write that named romFileIds the server no longer knows (400 "Tracks not
 * found"): a rescan re-issued the ids. Refetching the track list heals them.
 */
class StaleTrackException(cause: Throwable) :
    Exception("Library changed on the server - list refreshed, try again", cause)

/**
 * Broadcast raised whenever a repository detects stale romFileIds, so screens
 * holding track lists (e.g. the album) can refetch and pick up the fresh ids.
 */
@Singleton
class StaleTrackEvents @Inject constructor() {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyStale() {
        _events.tryEmit(Unit)
    }
}

/**
 * Maps a 400 "Tracks not found" server rejection to [StaleTrackException],
 * notifying [events]; any other failure passes through unchanged.
 */
internal fun Throwable.mapStaleTrackError(events: StaleTrackEvents): Throwable {
    if (!isStaleTrackError()) return this
    events.notifyStale()
    return StaleTrackException(this)
}

private const val HTTP_BAD_REQUEST = 400

private fun Throwable.isStaleTrackError(): Boolean {
    val http = this as? HttpException ?: return false
    if (http.code() != HTTP_BAD_REQUEST) return false
    val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull()
    return body?.contains("Tracks not found") == true
}
