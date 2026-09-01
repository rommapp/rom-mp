package com.rommapp.rommp.data.playback

import com.rommapp.rommp.data.music.Track

enum class RepeatMode { OFF, ALL, ONE }

/** Snapshot of the player for the UI. */
data class PlaybackState(
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Last playback failure; null while healthy, cleared on the next transition/play. */
    val error: String? = null,
) {
    val currentTrack: Track? get() = queue.getOrNull(currentIndex)
    val hasContent: Boolean get() = currentTrack != null
}
