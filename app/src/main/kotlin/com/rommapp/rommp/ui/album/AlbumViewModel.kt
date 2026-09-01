package com.rommapp.rommp.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.download.DownloadManager
import com.rommapp.rommp.data.download.DownloadProgress
import com.rommapp.rommp.data.music.CrateMembershipRepository
import com.rommapp.rommp.data.music.FavoritesRepository
import com.rommapp.rommp.data.music.GameWithMusic
import com.rommapp.rommp.data.music.MusicRepository
import com.rommapp.rommp.data.music.StaleTrackEvents
import com.rommapp.rommp.data.music.Track
import com.rommapp.rommp.data.network.NetworkMonitor
import com.rommapp.rommp.data.playback.PlaybackManager
import com.rommapp.rommp.data.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class AlbumUiState(
    val loading: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val error: String? = null,
    val coverUrl: String? = null,
    /** True when the list came from cache/downloads because the server was unreachable. */
    val offline: Boolean = false,
    /** Transient failure from a favorite toggle; the list stays on screen. */
    val favoriteError: String? = null,
)

data class DownloadUiState(
    val downloaded: Boolean = false,
    val progress: DownloadProgress? = null,
)

@HiltViewModel
class AlbumViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playbackManager: PlaybackManager,
    private val downloadManager: DownloadManager,
    private val networkMonitor: NetworkMonitor,
    private val crateMembership: CrateMembershipRepository,
    staleTrackEvents: StaleTrackEvents,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val romId: Int = savedStateHandle.get<Int>("romId") ?: -1
    val gameName: String = savedStateHandle.get<String>("gameName").orEmpty()

    private val _state = MutableStateFlow(AlbumUiState())
    val state: StateFlow<AlbumUiState> = _state.asStateFlow()

    val playback: StateFlow<PlaybackState> = playbackManager.state

    val download: StateFlow<DownloadUiState> =
        combine(downloadManager.downloadedIds, downloadManager.active) { ids, active ->
            DownloadUiState(downloaded = romId in ids, progress = active?.takeIf { it.romId == romId })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadUiState())

    val downloadedTrackIds: StateFlow<Set<Int>> = downloadManager.downloadedTrackIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val downloadingTrackIds: StateFlow<Set<Int>> = downloadManager.downloadingTrackIds

    val canFavorite: StateFlow<Boolean> = tokenStore.hasScope("playlists.write")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val favoriteOverrides: StateFlow<Map<Int, Boolean>> = favoritesRepository.overrides

    /** rom_file_id -> crate ids; drives the row save button's saved state. */
    val membership: StateFlow<Map<Int, Set<Int>>> = crateMembership.membership

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online -> load(online) }
        }
        viewModelScope.launch {
            if (tokenStore.hasScope("playlists.write").first()) {
                runCatching { crateMembership.refresh() }
            }
        }
        viewModelScope.launch {
            staleTrackEvents.events.collect { load(networkMonitor.isOnline.value) }
        }
    }

    fun playAll() = playFrom(0)
    fun playTrack(index: Int) = playFrom(index)
    fun cycleRepeat() = playbackManager.cycleRepeat()
    fun toggleShuffle() = playbackManager.setShuffle(!playback.value.shuffle)

    fun download() {
        val tracks = _state.value.tracks
        if (tracks.isEmpty()) return
        val game = GameWithMusic(romId, gameName, tracks.first().platformName, _state.value.coverUrl)
        downloadManager.downloadAlbum(game, tracks)
    }

    fun deleteDownload() = downloadManager.deleteAlbum(romId)

    fun downloadTrack(track: Track) {
        val game = GameWithMusic(romId, gameName, track.platformName, _state.value.coverUrl)
        downloadManager.downloadTrack(game, track)
    }

    fun toggleFavorite(track: Track) {
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

    fun retry() = load(networkMonitor.isOnline.value)

    private fun playFrom(index: Int) {
        viewModelScope.launch {
            val displayed = _state.value.tracks
            if (displayed.isEmpty()) return@launch
            val localByRomFileId = downloadManager.downloadedTracks(romId).associateBy { it.romFileId }
            val queue = displayed.map { track ->
                localByRomFileId[track.romFileId]
                    ?.let { local -> track.copy(streamUrl = local.streamUrl) }
                    ?: track
            }
            playbackManager.playQueue(queue, index)
        }
    }

    private fun load(online: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = it.tracks.isEmpty(), error = null) }
            if (online) {
                runCatching { repository.gameTracks(romId) }
                    .onSuccess { tracks ->
                        _state.update {
                            it.copy(loading = false, tracks = tracks, offline = false, coverUrl = tracks.firstOrNull()?.albumCoverUrl)
                        }
                    }
                    .onFailure { e ->
                        if (e.isAuthError()) {
                            _state.update {
                                it.copy(loading = false, error = "Session expired - re-pair with your RomM server")
                            }
                        } else {
                            showOffline(e)
                        }
                    }
            } else {
                showOffline(null)
            }
        }
    }

    /** 401/403 from the roms.read-scoped music API means a revoked/expired token. */
    private fun Throwable.isAuthError(): Boolean =
        (this as? HttpException)?.code()?.let { it == 401 || it == 403 } == true

    private suspend fun showOffline(error: Throwable?) {
        // Show the full known list from cache (or downloads); the UI greys out
        // tracks that aren't downloaded.
        val cached = repository.cachedTracks(romId)
        val fallback = cached.ifEmpty { downloadManager.downloadedTracks(romId) }
        if (fallback.isNotEmpty()) {
            _state.update {
                it.copy(loading = false, tracks = fallback, offline = true, coverUrl = fallback.firstOrNull()?.albumCoverUrl)
            }
        } else {
            _state.update { it.copy(loading = false, error = error?.message ?: "Offline - nothing downloaded") }
        }
    }
}
