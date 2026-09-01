package com.rommapp.rommp.ui.playlists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.music.Playlist
import com.rommapp.rommp.data.music.PlaylistsRepository
import com.rommapp.rommp.data.music.Track
import com.rommapp.rommp.data.playback.PlaybackManager
import com.rommapp.rommp.data.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistDetailUiState(
    val loading: Boolean = true,
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
    /** Transient failure from a reorder/remove/toggle; the list stays on screen. */
    val actionError: String? = null,
    val renameOpen: Boolean = false,
    /** Failure inside the rename sheet (e.g. duplicate name); the sheet stays open. */
    val renameError: String? = null,
    val deleteConfirmOpen: Boolean = false,
    /** Index of the track whose actions sheet is open, or null. */
    val trackActionsIndex: Int? = null,
    /** Set once the crate is deleted server-side; the screen navigates back. */
    val deleted: Boolean = false,
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistsRepository: PlaylistsRepository,
    private val playbackManager: PlaybackManager,
    tokenStore: TokenStore,
) : ViewModel() {

    private val playlistId: Int = savedStateHandle.get<Int>("playlistId") ?: -1

    private val _state = MutableStateFlow(PlaylistDetailUiState())
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()

    val playback: StateFlow<PlaybackState> = playbackManager.state

    val canWrite: StateFlow<Boolean> = tokenStore.hasScope("playlists.write")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun retry() = load()

    /** Called on every screen entry, so the list reflects crate-sheet mutations. */
    fun refresh() = load()

    fun playAll() = playFrom(0)
    fun playTrack(index: Int) = playFrom(index)

    fun openTrackActions(index: Int) = _state.update { it.copy(trackActionsIndex = index) }
    fun closeTrackActions() = _state.update { it.copy(trackActionsIndex = null) }

    /** Moves the track at [index] by [delta] rows and persists the full new order. */
    fun moveTrack(index: Int, delta: Int) {
        val tracks = _state.value.tracks
        val target = index + delta
        if (index !in tracks.indices || target !in tracks.indices) return
        val reordered = tracks.toMutableList().apply { add(target, removeAt(index)) }
        _state.update { it.copy(tracks = reordered, actionError = null, trackActionsIndex = null) }
        viewModelScope.launch {
            runCatching { playlistsRepository.reorderTracks(playlistId, reordered.map { it.romFileId }) }
                .onFailure { e ->
                    // Inverse single move on the current list, so concurrent
                    // edits made meanwhile are not clobbered by a snapshot.
                    _state.update { s ->
                        val reverted = s.tracks.toMutableList().apply {
                            if (target in indices) add(index.coerceAtMost(size), removeAt(target))
                        }
                        s.copy(tracks = reverted, actionError = e.message ?: "Could not reorder")
                    }
                }
        }
    }

    fun removeTrack(index: Int) {
        val tracks = _state.value.tracks
        val track = tracks.getOrNull(index) ?: return
        val remaining = tracks.toMutableList().apply { removeAt(index) }
        _state.update {
            it.copy(
                tracks = remaining,
                playlist = it.playlist?.let { p -> p.copy(trackCount = (p.trackCount - 1).coerceAtLeast(0)) },
                actionError = null,
                trackActionsIndex = null,
            )
        }
        viewModelScope.launch {
            runCatching { playlistsRepository.removeTracks(playlistId, listOf(track.romFileId)) }
                .onFailure { e ->
                    // Re-insert just the removed track into the current list, so
                    // concurrent edits made meanwhile are not clobbered by a snapshot.
                    _state.update { s ->
                        val restored = s.tracks.toMutableList().apply { add(index.coerceAtMost(size), track) }
                        s.copy(
                            tracks = restored,
                            playlist = s.playlist?.let { p -> p.copy(trackCount = p.trackCount + 1) },
                            actionError = e.message ?: "Could not remove track",
                        )
                    }
                }
        }
    }

    fun setRenameOpen(open: Boolean) =
        _state.update { it.copy(renameOpen = open, renameError = null) }

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(renameError = null) }
        viewModelScope.launch {
            runCatching { playlistsRepository.updatePlaylist(playlistId, name = trimmed) }
                .onSuccess { playlist ->
                    _state.update { it.copy(playlist = playlist, renameOpen = false) }
                }
                .onFailure { e ->
                    _state.update { it.copy(renameError = e.message ?: "Could not rename crate") }
                }
        }
    }

    fun setDeleteConfirmOpen(open: Boolean) =
        _state.update { it.copy(deleteConfirmOpen = open) }

    fun deletePlaylist() {
        viewModelScope.launch {
            runCatching { playlistsRepository.deletePlaylist(playlistId) }
                .onSuccess { _state.update { it.copy(deleteConfirmOpen = false, deleted = true) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(deleteConfirmOpen = false, actionError = e.message ?: "Could not delete crate")
                    }
                }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }

    private fun playFrom(index: Int) {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        playbackManager.playQueue(tracks, index)
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.tracks.isEmpty(), error = null) }
            runCatching {
                coroutineScope {
                    val playlist = async { playlistsRepository.playlist(playlistId) }
                    val tracks = async { playlistsRepository.playlistTracks(playlistId) }
                    playlist.await() to tracks.await()
                }
            }
                .onSuccess { (playlist, tracks) ->
                    _state.update {
                        it.copy(loading = false, playlist = playlist, tracks = tracks, error = null)
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "Could not load crate") }
                }
        }
    }
}
