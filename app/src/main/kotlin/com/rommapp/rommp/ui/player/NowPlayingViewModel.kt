package com.rommapp.rommp.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.music.CrateMembershipRepository
import com.rommapp.rommp.data.music.FavoritesRepository
import com.rommapp.rommp.data.playback.PlaybackManager
import com.rommapp.rommp.data.playback.PlaybackState
import com.rommapp.rommp.data.playback.PlayerLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Screen-local interaction state (touch-lock, queue overlay, crate sheet). */
data class PlayerUiState(
    val locked: Boolean = false,
    val queueOpen: Boolean = false,
    /** The add-to-crate sheet, acting on the current track. */
    val addToCrateOpen: Boolean = false,
    /** Transient failure from a favorite toggle. */
    val error: String? = null,
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val favoritesRepository: FavoritesRepository,
    private val crateMembership: CrateMembershipRepository,
    private val tokenStore: TokenStore,
    private val lockController: PlayerLockController,
) : ViewModel() {
    val state: StateFlow<PlaybackState> = playbackManager.state

    /** Rotate-shut/open lock requests from the Activity; collected only while the player is visible. */
    val lockRequests: SharedFlow<Unit> = lockController.lockRequests
    val unlockRequests: SharedFlow<Unit> = lockController.unlockRequests

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    val canFavorite: StateFlow<Boolean> = tokenStore.hasScope("playlists.write")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The current track's favorite state, with optimistic overrides applied; drives the deck LED. */
    val isFavorite: StateFlow<Boolean> =
        combine(playbackManager.state, favoritesRepository.overrides) { playback, overrides ->
            playback.currentTrack?.let { overrides[it.romFileId] ?: it.isFavorite } ?: false
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Saved = liked or in at least one crate; drives the deck save button's glyph. */
    val isSaved: StateFlow<Boolean> = combine(
        playbackManager.state,
        favoritesRepository.overrides,
        crateMembership.membership,
    ) { playback, overrides, membership ->
        val track = playback.currentTrack ?: return@combine false
        val favorite = overrides[track.romFileId] ?: track.isFavorite
        val inCrate = membership[track.romFileId]?.isNotEmpty() == true
        favorite || inCrate
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            if (tokenStore.hasScope("playlists.write").first()) {
                runCatching { crateMembership.refresh() }
            }
        }
    }

    fun togglePlayPause() = playbackManager.togglePlayPause()
    fun next() = playbackManager.next()
    fun previous() = playbackManager.previous()
    fun playIndex(index: Int) = playbackManager.playIndex(index)
    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)

    fun toggleShuffle() = playbackManager.setShuffle(!state.value.shuffle)
    fun cycleRepeat() = playbackManager.cycleRepeat()

    fun toggleFavorite() {
        val track = state.value.currentTrack ?: return
        val next = !isFavorite.value
        _ui.update { it.copy(error = null) }
        viewModelScope.launch {
            runCatching { favoritesRepository.setFavorite(track.romFileId, next) }
                .onFailure { e ->
                    _ui.update { it.copy(error = e.message ?: "Could not update favorite") }
                }
        }
    }

    fun clearError() = _ui.update { it.copy(error = null) }

    fun toggleLock() = _ui.update { it.copy(locked = !it.locked) }

    /** Idempotent engage (rotate-shut): a re-shut while already locked stays locked. */
    fun lock() = _ui.update { it.copy(locked = true) }
    fun unlock() = _ui.update { it.copy(locked = false) }

    /** Arms/disarms Activity-level F12 handling for the player's visible window. */
    fun setPlayerVisible(visible: Boolean) = lockController.playerVisible.set(visible)
    fun toggleQueue() = _ui.update { it.copy(queueOpen = !it.queueOpen) }
    fun closeQueue() = _ui.update { it.copy(queueOpen = false) }
    fun openAddToCrate() = _ui.update { it.copy(addToCrateOpen = true) }
    fun closeAddToCrate() = _ui.update { it.copy(addToCrateOpen = false) }
}
