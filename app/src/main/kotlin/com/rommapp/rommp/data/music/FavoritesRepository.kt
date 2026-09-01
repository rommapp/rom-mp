package com.rommapp.rommp.data.music

import kotlinx.coroutines.flow.StateFlow

/**
 * The user's liked tracks via RomM's favorites API. Implemented by
 * [RemoteFavoritesRepository]; an interface so a fake can back UI development.
 */
interface FavoritesRepository {
    /**
     * Optimistic isFavorite overrides keyed by romFileId, layered over the
     * is_favorite field on server track rows. Shared so every screen renders
     * a toggle immediately, before (or without) a refetch.
     */
    val overrides: StateFlow<Map<Int, Boolean>>

    /** All the user's liked tracks, in title order; pages of [limit] are followed from [offset]. */
    suspend fun favorites(limit: Int = 500, offset: Int = 0): List<Track>

    /**
     * Flips a track's favorite state on the server. The override is applied
     * before the call and reverted (then rethrown) on failure; a rescan-dead
     * [romFileId] surfaces as [StaleTrackException].
     */
    suspend fun setFavorite(romFileId: Int, favorite: Boolean)
}
