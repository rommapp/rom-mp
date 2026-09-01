package com.rommapp.rommp.data.music

/**
 * RomM music playlists: CRUD, track membership, and ordering. Implemented by
 * [RemotePlaylistsRepository]; write ops require ownership (the server 403s
 * otherwise), which surfaces through [Playlist.isOwned].
 */
interface PlaylistsRepository {
    /** The user's playlists plus other users' public ones. */
    suspend fun playlists(): List<Playlist>

    suspend fun playlist(id: Int): Playlist

    /** Throws on a duplicate name (server 400). */
    suspend fun createPlaylist(
        name: String,
        description: String? = null,
        isPublic: Boolean = false,
    ): Playlist

    /** Null fields are left unchanged. */
    suspend fun updatePlaylist(
        id: Int,
        name: String? = null,
        description: String? = null,
        isPublic: Boolean? = null,
    ): Playlist

    suspend fun deletePlaylist(id: Int)

    /** All resolvable tracks in playlist position order; pages of [limit] are followed from [offset]. */
    suspend fun playlistTracks(id: Int, limit: Int = 500, offset: Int = 0): List<Track>

    /**
     * Identities of all resolvable tracks in the playlist, without cover
     * resolution; the cheap fetch behind the crate-membership index.
     */
    suspend fun playlistTrackRefs(id: Int, limit: Int = 500): List<TrackRef>

    /**
     * Appends; already-present tracks are ignored. Returns how many were
     * added. Throws [StaleTrackException] when the server no longer knows the
     * ids (rescan re-issued them).
     */
    suspend fun addTracks(id: Int, romFileIds: List<Int>): Int

    /** Throws [StaleTrackException] like [addTracks]. */
    suspend fun removeTracks(id: Int, romFileIds: List<Int>): Int

    /** Persists the full desired order of the playlist's resolvable tracks. */
    suspend fun reorderTracks(id: Int, romFileIds: List<Int>)
}
