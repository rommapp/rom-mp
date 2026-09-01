package com.rommapp.rommp.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rommapp.rommp.data.music.CrateMembershipRepository
import com.rommapp.rommp.data.music.FavoritesRepository
import com.rommapp.rommp.data.music.Playlist
import com.rommapp.rommp.data.music.PlaylistsRepository
import com.rommapp.rommp.data.music.StaleTrackException
import com.rommapp.rommp.data.music.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddToCrateUiState(
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class AddToCrateViewModel @Inject constructor(
    private val crateMembership: CrateMembershipRepository,
    private val playlistsRepository: PlaylistsRepository,
    private val favoritesRepository: FavoritesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddToCrateUiState())
    val state: StateFlow<AddToCrateUiState> = _state.asStateFlow()

    /** Owned crates with live counts; tracks cannot be added to other users' crates. */
    val crates: StateFlow<List<Playlist>> = crateMembership.ownedCrates

    /** rom_file_id -> ids of crates containing that track, for the checkmarks. */
    val membership: StateFlow<Map<Int, Set<Int>>> = crateMembership.membership

    val favoriteOverrides: StateFlow<Map<Int, Boolean>> = favoritesRepository.overrides

    /** Called on every sheet open: refreshes crates and their member tracks. */
    fun open() {
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            _state.update { it.copy(loading = crates.value.isEmpty()) }
            runCatching { crateMembership.refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Could not load crates") }
                }
            _state.update { it.copy(loading = false) }
        }
    }

    /** Adds when the crate lacks the track, removes when it already has it. */
    fun toggleCrate(playlist: Playlist, track: Track) {
        val add = playlist.id !in membership.value[track.romFileId].orEmpty()
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            runCatching { crateMembership.toggleCrate(track, playlist.id, add) }
                .onFailure { e ->
                    val fallback = if (add) "Could not add to crate" else "Could not remove from crate"
                    handleActionError(e, fallback)
                }
        }
    }

    fun createAndAdd(name: String, track: Track) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            val playlist = runCatching { playlistsRepository.createPlaylist(trimmed) }
                .getOrElse { e ->
                    _state.update { it.copy(error = e.message ?: "Could not create crate") }
                    return@launch
                }
            // The crate exists server-side now, so it stays in the list even if
            // the follow-up add fails.
            crateMembership.registerCrate(playlist)
            runCatching { crateMembership.toggleCrate(track, playlist.id, add = true) }
                .onFailure { e ->
                    handleActionError(e, "Crate created, but the track could not be added")
                }
        }
    }

    fun toggleFavorite(track: Track) {
        val current = favoriteOverrides.value[track.romFileId] ?: track.isFavorite
        _state.update { it.copy(error = null) }
        viewModelScope.launch {
            runCatching { favoritesRepository.setFavorite(track.romFileId, !current) }
                .onFailure { e -> handleActionError(e, "Could not update favorite") }
        }
    }

    /**
     * Stale romFileIds already broadcast an album refetch from the data layer;
     * here the membership index is rebuilt and the message shown inline. The
     * write is not retried automatically.
     */
    private fun handleActionError(e: Throwable, fallback: String) {
        _state.update { it.copy(error = e.message ?: fallback) }
        if (e is StaleTrackException) {
            viewModelScope.launch { runCatching { crateMembership.refresh() } }
        }
    }
}
