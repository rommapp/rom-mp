package com.rommapp.rommp.data.music

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultCrateMembershipRepository @Inject constructor(
    private val playlistsRepository: PlaylistsRepository,
) : CrateMembershipRepository {

    private val _ownedCrates = MutableStateFlow<List<Playlist>>(emptyList())
    override val ownedCrates: StateFlow<List<Playlist>> = _ownedCrates.asStateFlow()

    private val _membership = MutableStateFlow<Map<Int, Set<Int>>>(emptyMap())
    override val membership: StateFlow<Map<Int, Set<Int>>> = _membership.asStateFlow()

    private val refreshMutex = Mutex()

    private var refreshing = false
    private val pendingOps = mutableListOf<PendingOp>()

    private sealed interface PendingOp {
        data class Delta(val romFileId: Int, val playlistId: Int, val add: Boolean) : PendingOp
        data class Register(val playlist: Playlist) : PendingOp
    }

    override suspend fun refresh() {
        refreshMutex.withLock {
            refreshing = true
            pendingOps.clear()
            try {
                val crates = playlistsRepository.playlists().filter { it.isOwned }
                val built = mutableMapOf<Int, MutableSet<Int>>()
                for (crate in crates) {
                    playlistsRepository.playlistTrackRefs(crate.id).forEach { ref ->
                        built.getOrPut(ref.romFileId) { mutableSetOf() } += crate.id
                    }
                }
                refreshing = false
                _ownedCrates.value = crates
                _membership.value = built
                pendingOps.forEach { op ->
                    when (op) {
                        is PendingOp.Register -> addOwnedCrate(op.playlist)
                        is PendingOp.Delta -> applyDelta(op.romFileId, op.playlistId, op.add)
                    }
                }
            } finally {
                refreshing = false
                pendingOps.clear()
            }
        }
    }

    override fun registerCrate(playlist: Playlist) {
        if (refreshing) pendingOps += PendingOp.Register(playlist)
        addOwnedCrate(playlist)
    }

    override suspend fun toggleCrate(track: Track, playlistId: Int, add: Boolean) {
        val romFileId = track.romFileId
        val applied = applyDelta(romFileId, playlistId, add)
        runCatching {
            if (add) {
                playlistsRepository.addTracks(playlistId, listOf(romFileId))
            } else {
                playlistsRepository.removeTracks(playlistId, listOf(romFileId))
            }
        }.onFailure { error ->
            if (applied) applyDelta(romFileId, playlistId, !add)
            throw error
        }
    }

    private fun addOwnedCrate(playlist: Playlist) {
        _ownedCrates.update { crates ->
            if (crates.any { it.id == playlist.id }) crates else crates + playlist
        }
    }

    /**
     * Adds/removes [playlistId] in [romFileId]'s crate set and bumps the crate's
     * count; returns false (and changes nothing) when already in that state.
     */
    private fun applyDelta(romFileId: Int, playlistId: Int, add: Boolean): Boolean {
        var changed = false
        _membership.update { current ->
            val set = current[romFileId].orEmpty()
            changed = if (add) playlistId !in set else playlistId in set
            if (!changed) return@update current
            val newSet = if (add) set + playlistId else set - playlistId
            if (newSet.isEmpty()) current - romFileId else current + (romFileId to newSet)
        }
        if (changed) {
            if (refreshing) pendingOps += PendingOp.Delta(romFileId, playlistId, add)
            val delta = if (add) 1 else -1
            _ownedCrates.update { crates ->
                crates.map { crate ->
                    if (crate.id == playlistId) {
                        crate.copy(trackCount = (crate.trackCount + delta).coerceAtLeast(0))
                    } else {
                        crate
                    }
                }
            }
        }
        return changed
    }
}
