package com.rommapp.rommp.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.download.DownloadManager
import com.rommapp.rommp.data.music.GameWithMusic
import com.rommapp.rommp.data.music.MusicRepository
import com.rommapp.rommp.data.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class LibraryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val allGames: List<GameWithMusic> = emptyList(),
    val query: String = "",
    val searchActive: Boolean = false,
    val filterOpen: Boolean = false,
    val selectedPlatforms: Set<String> = emptySet(),
    val downloadedIds: Set<Int> = emptySet(),
    val offline: Boolean = false,
) {
    /** Distinct platforms present in the music library, for the filter panel. */
    val platforms: List<String>
        get() = allGames.map { it.platformName }.filter { it.isNotBlank() }.distinct().sorted()

    /** Games after applying the search text and platform filter (client-side). */
    val games: List<GameWithMusic>
        get() = allGames.filter { game ->
            (query.isBlank() || game.name.contains(query, ignoreCase = true)) &&
                (selectedPlatforms.isEmpty() || game.platformName in selectedPlatforms)
        }

    val hasActiveFilter: Boolean get() = selectedPlatforms.isNotEmpty()
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val downloadManager: DownloadManager,
    private val networkMonitor: NetworkMonitor,
    tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** Gates the Favorites shelf case and the Crate entry; needs playlists.read. */
    val canSeeFavorites: StateFlow<Boolean> = tokenStore.hasScope("playlists.read")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // Re-evaluate the library whenever connectivity flips.
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online -> reload(online) }
        }
        viewModelScope.launch {
            downloadManager.downloadedIds.collect { ids -> _state.update { it.copy(downloadedIds = ids) } }
        }
    }

    fun setSearchActive(active: Boolean) {
        _state.update { it.copy(searchActive = active, query = if (active) it.query else "") }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setFilterOpen(open: Boolean) {
        _state.update { it.copy(filterOpen = open) }
    }

    fun togglePlatform(platform: String) {
        _state.update { s ->
            val next = if (platform in s.selectedPlatforms) {
                s.selectedPlatforms - platform
            } else {
                s.selectedPlatforms + platform
            }
            s.copy(selectedPlatforms = next)
        }
    }

    fun clearFilters() {
        _state.update { it.copy(selectedPlatforms = emptySet()) }
    }

    fun retry() = reload(networkMonitor.isOnline.value)

    private fun reload(online: Boolean) {
        viewModelScope.launch {
            // Only show the full-screen spinner on the very first load.
            _state.update { it.copy(loading = it.allGames.isEmpty(), error = null) }
            if (online) {
                runCatching { repository.gamesWithMusic(null) }
                    .onSuccess { games ->
                        _state.update { it.copy(loading = false, allGames = games, offline = false, error = null) }
                    }
                    .onFailure { e ->
                        if (e.isAuthError()) {
                            _state.update {
                                it.copy(loading = false, error = "Session expired - re-pair with your RomM server")
                            }
                        } else {
                            showDownloaded(e)
                        }
                    }
            } else {
                showDownloaded()
            }
        }
    }

    /** 401/403 from the roms.read-scoped roms API means a revoked/expired token. */
    private fun Throwable.isAuthError(): Boolean =
        (this as? HttpException)?.code()?.let { it == 401 || it == 403 } == true

    private suspend fun showDownloaded(cause: Throwable? = null) {
        val downloaded = downloadManager.downloadedGames.first()
        if (downloaded.isEmpty() && cause != null) {
            _state.update { it.copy(loading = false, error = cause.message ?: "Could not load the library") }
        } else {
            _state.update { it.copy(loading = false, allGames = downloaded, offline = true) }
        }
    }
}
