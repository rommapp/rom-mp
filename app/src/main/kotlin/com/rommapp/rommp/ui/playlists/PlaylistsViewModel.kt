package com.rommapp.rommp.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.music.Playlist
import com.rommapp.rommp.data.music.PlaylistsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val loading: Boolean = true,
    val playlists: List<Playlist> = emptyList(),
    val error: String? = null,
    /** The new-crate sheet. */
    val createOpen: Boolean = false,
    /** Failure inside the create sheet (e.g. duplicate name); the sheet stays open. */
    val createError: String? = null,
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistsRepository: PlaylistsRepository,
    tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaylistsUiState())
    val state: StateFlow<PlaylistsUiState> = _state.asStateFlow()

    val canWrite: StateFlow<Boolean> = tokenStore.hasScope("playlists.write")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun retry() = load()

    /** Called on every screen entry, so counts reflect crate-sheet mutations. */
    fun refresh() = load()

    fun setCreateOpen(open: Boolean) =
        _state.update { it.copy(createOpen = open, createError = null) }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(createError = null) }
        viewModelScope.launch {
            runCatching { playlistsRepository.createPlaylist(trimmed) }
                .onSuccess { playlist ->
                    _state.update {
                        it.copy(playlists = it.playlists + playlist, createOpen = false)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(createError = e.message ?: "Could not create crate") }
                }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.playlists.isEmpty(), error = null) }
            runCatching { playlistsRepository.playlists() }
                .onSuccess { playlists ->
                    _state.update { it.copy(loading = false, playlists = playlists, error = null) }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Could not load crates") }
                }
        }
    }
}
