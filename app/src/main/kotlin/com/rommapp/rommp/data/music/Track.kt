package com.rommapp.rommp.data.music

/** A playable soundtrack track with absolute URLs, for the album view + player. */
data class Track(
    val romFileId: Int,
    val romId: Int,
    val title: String,
    val gameName: String,
    val platformName: String,
    val trackNumber: Int?,
    val durationSeconds: Double?,
    /** Absolute stream URL (needs the Bearer header when played). */
    val streamUrl: String,
    /** Per-track cover (embedded soundtrack art), or null. */
    val coverUrl: String?,
    /** The game's box art — the outer/album identity (a game may have several OSTs). */
    val gameCoverUrl: String? = null,
    val isFavorite: Boolean = false,
) {
    /** The cover to show for album/vinyl/mini-player: game art, else the track's. */
    val albumCoverUrl: String? get() = gameCoverUrl ?: coverUrl
}

/** Minimal identity of a playlist member row, for the crate-membership index. */
data class TrackRef(
    val romFileId: Int,
)
