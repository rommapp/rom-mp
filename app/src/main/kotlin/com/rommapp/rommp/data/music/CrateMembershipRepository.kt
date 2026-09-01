package com.rommapp.rommp.data.music

import kotlinx.coroutines.flow.StateFlow

/**
 * Client-side index of which owned crates (playlists) contain which tracks,
 * keyed on rom_file_id (the id the server keys playlist membership on). Backs
 * the add-to-crate sheet: checkmarks and live counts come from here, toggles
 * mutate it optimistically before the server call. Implemented by
 * [DefaultCrateMembershipRepository].
 */
interface CrateMembershipRepository {
    /** Owned crates with live trackCount, bumped optimistically on toggles. */
    val ownedCrates: StateFlow<List<Playlist>>

    /** rom_file_id -> ids of owned crates containing that track. */
    val membership: StateFlow<Map<Int, Set<Int>>>

    /** Refetches owned crates and each one's member tracks. */
    suspend fun refresh()

    /** Makes a just-created crate visible before the next [refresh]. */
    fun registerCrate(playlist: Playlist)

    /**
     * Adds/removes [track] to/from the crate. The index and count are updated
     * before the server call; on failure only this operation's delta is
     * reverted and the error is rethrown ([StaleTrackException] when the
     * server no longer knows the track's romFileId).
     */
    suspend fun toggleCrate(track: Track, playlistId: Int, add: Boolean)
}
