package com.rommapp.rommp.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.music.CrateMembershipRepository
import com.rommapp.rommp.data.music.FavoritesRepository
import com.rommapp.rommp.data.music.StaleTrackEvents
import com.rommapp.rommp.data.music.Track
import com.rommapp.rommp.data.playback.PlaybackManager
import com.rommapp.rommp.data.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavoritesUiState(
    val loading: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
    /** Transient failure from a favorite toggle; the list stays on screen. */
    val favoriteError: String? = null,
)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val playbackManager: PlaybackManager,
    private val crateMembership: CrateMembershipRepository,
    staleTrackEvents: StaleTrackEvents,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(FavoritesUiState())
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    val playback: StateFlow<PlaybackState> = playbackManager.state

    val canFavorite: StateFlow<Boolean> = tokenStore.hasScope("playlists.write")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val favoriteOverrides: StateFlow<Map<Int, Boolean>> = favoritesRepository.overrides

    /** rom_file_id -> crate ids; drives the row save button's saved state. */
    val membership: StateFlow<Map<Int, Set<Int>>> = crateMembership.membership

    init {
        load()
        viewModelScope.launch {
            if (tokenStore.hasScope("playlists.write").first()) {
                runCatching { crateMembership.refresh() }
            }
        }
        // A write hit rescan-dead romFileIds (possibly from the crate sheet):
        // refetch so rows carry the fresh ids and a manual retry can succeed.
        viewModelScope.launch {
            staleTrackEvents.events.collect { load() }
        }
    }

    fun playAll() = playFrom(0)
    fun playTrack(index: Int) = playFrom(index)

    fun toggleFavorite(track: Track) {
        // Unfavorited rows stay in the list with an open heart, so a tap undoes.
        val current = favoriteOverrides.value[track.romFileId] ?: track.isFavorite
        _state.update { it.copy(favoriteError = null) }
        viewModelScope.launch {
            runCatching { favoritesRepository.setFavorite(track.romFileId, !current) }
                .onFailure { e ->
                    _state.update { it.copy(favoriteError = e.message ?: "Could not update favorite") }
                }
        }
    }

    fun clearFavoriteError() = _state.update { it.copy(favoriteError = null) }

    fun retry() = load()

    private fun playFrom(index: Int) {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        playbackManager.playQueue(tracks, index)
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.tracks.isEmpty(), error = null) }
            runCatching { favoritesRepository.favorites() }
                .onSuccess { tracks ->
                    _state.update { it.copy(loading = false, tracks = tracks, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Could not load favorites") }
                }
        }
    }
}
