package com.rommapp.rommp.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rommapp.rommp.data.music.Track
import com.rommapp.rommp.data.playback.RepeatMode
import com.rommapp.rommp.ui.TightTouchSlop
import com.rommapp.rommp.ui.input.FocusHalo
import com.rommapp.rommp.ui.input.FocusableIconButton
import com.rommapp.rommp.ui.input.FocusableRow
import com.rommapp.rommp.ui.input.cursorSection
import com.rommapp.rommp.ui.player.MiniPlayer
import com.rommapp.rommp.ui.playlists.AddToCrateSheet
import com.rommapp.rommp.ui.playlists.SaveTrackButton
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.Cream
import com.rommapp.rommp.ui.theme.LedLocked
import com.rommapp.rommp.ui.theme.MutedText
import kotlinx.coroutines.delay

private val AlbumBackground = Color(0xFF17110C)
private val HeaderBackground = Color(0xFF241B14)

@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloadingTrackIds by viewModel.downloadingTrackIds.collectAsStateWithLifecycle()
    val canFavorite by viewModel.canFavorite.collectAsStateWithLifecycle()
    val favoriteOverrides by viewModel.favoriteOverrides.collectAsStateWithLifecycle()
    var crateTrack by remember { mutableStateOf<Track?>(null) }
    val listFocus = remember { FocusRequester() }
    val headerFocus = remember { FocusRequester() }
    val hasTracks = state.tracks.isNotEmpty()
    LaunchedEffect(crateTrack, hasTracks) {
        if (crateTrack != null) return@LaunchedEffect
        withFrameNanos { }
        runCatching { if (hasTracks) listFocus.requestFocus() else headerFocus.requestFocus() }
    }
    LaunchedEffect(state.tracks) {
        crateTrack = crateTrack?.let { current ->
            state.tracks.firstOrNull { it.romFileId == current.romFileId } ?: current
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.ButtonB) {
                    if (crateTrack != null) crateTrack = null else onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(AlbumBackground),
        ) {
            AlbumHeader(
                gameName = viewModel.gameName,
                coverUrl = state.coverUrl,
                repeatMode = playback.repeatMode,
                shuffle = playback.shuffle,
                download = download,
                headerFocus = headerFocus,
                onBack = onBack,
                onPlayAll = { viewModel.playAll(); onOpenPlayer() },
                onToggleRepeat = viewModel::cycleRepeat,
                onToggleShuffle = viewModel::toggleShuffle,
                onDownload = viewModel::download,
                onDeleteDownload = viewModel::deleteDownload,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> Center { CircularProgressIndicator() }
                    state.error != null -> Center {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error!!)
                            FocusHalo(onActivate = viewModel::retry) {
                                TextButton(onClick = viewModel::retry) { Text("Retry") }
                            }
                        }
                    }
                    state.tracks.isEmpty() -> Center { Text("No music tracks") }
                    else -> TrackList(
                        tracks = state.tracks,
                        playingId = playback.currentTrack?.romFileId,
                        downloadedIds = downloadedTrackIds,
                        downloadingIds = downloadingTrackIds,
                        offline = state.offline,
                        showSave = canFavorite,
                        favoriteOverrides = favoriteOverrides,
                        listFocus = listFocus,
                        onPlay = { index -> viewModel.playTrack(index); onOpenPlayer() },
                        onDownload = viewModel::downloadTrack,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onManageSaved = { crateTrack = it },
                    )
                }
            }
            state.favoriteError?.let { message ->
                LaunchedEffect(message) {
                    delay(3_000)
                    viewModel.clearFavoriteError()
                }
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedLocked,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            MiniPlayer(onOpen = onOpenPlayer)
        }
        crateTrack?.let { track ->
            AddToCrateSheet(track = track, onDismiss = { crateTrack = null })
        }
    }
}

@Composable
private fun AlbumHeader(
    gameName: String,
    coverUrl: String?,
    repeatMode: RepeatMode,
    shuffle: Boolean,
    download: DownloadUiState,
    headerFocus: FocusRequester,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HeaderBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .cursorSection(headerFocus),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusableIconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                gameName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            DownloadControl(download, onDownload, onDeleteDownload)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusHalo(onActivate = onPlayAll) {
                Button(onClick = onPlayAll) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play All")
                }
            }
            Spacer(Modifier.weight(1f))
            FocusableIconButton(onClick = onToggleRepeat) {
                Icon(
                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = "Repeat",
                    tint = toggleTint(repeatMode != RepeatMode.OFF),
                )
            }
            FocusableIconButton(onClick = onToggleShuffle) {
                Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = toggleTint(shuffle))
            }
        }
    }
}

@Composable
private fun DownloadControl(state: DownloadUiState, onDownload: () -> Unit, onDelete: () -> Unit) {
    when {
        state.progress != null -> {
            val p = state.progress
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { if (p.total > 0) p.done.toFloat() / p.total else 0f },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        state.downloaded -> FocusableIconButton(onClick = onDelete) {
            Icon(Icons.Filled.DownloadDone, contentDescription = "Downloaded", tint = Brass)
        }
        else -> FocusableIconButton(onClick = onDownload) {
            Icon(Icons.Filled.Download, contentDescription = "Download", tint = Cream)
        }
    }
}

@Composable
private fun toggleTint(active: Boolean): Color =
    if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

@Composable
private fun TrackList(
    tracks: List<Track>,
    playingId: Int?,
    downloadedIds: Set<Int>,
    downloadingIds: Set<Int>,
    offline: Boolean,
    showSave: Boolean,
    favoriteOverrides: Map<Int, Boolean>,
    listFocus: FocusRequester,
    onPlay: (Int) -> Unit,
    onDownload: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onManageSaved: (Track) -> Unit,
) {
    TightTouchSlop {
        LazyColumn(Modifier.fillMaxSize().cursorSection(listFocus)) {
            itemsIndexed(tracks, key = { _, t -> t.romFileId }) { index, track ->
                val downloaded = track.romFileId in downloadedIds
                val downloading = track.romFileId in downloadingIds
                val available = downloaded || !offline
                val favorite = favoriteOverrides[track.romFileId] ?: track.isFavorite
                TrackRow(
                    track = track,
                    isPlaying = track.romFileId == playingId,
                    downloaded = downloaded,
                    downloading = downloading,
                    available = available,
                    showSave = showSave,
                    favorite = favorite,
                    onClick = { if (available) onPlay(index) },
                    onDownload = { onDownload(track) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onManageSaved = { onManageSaved(track) },
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isPlaying: Boolean,
    downloaded: Boolean,
    downloading: Boolean,
    available: Boolean,
    showSave: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onDownload: () -> Unit,
    onToggleFavorite: () -> Unit,
    onManageSaved: () -> Unit,
) {
    val titleColor = when {
        isPlaying -> MaterialTheme.colorScheme.primary
        !available -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = MaterialTheme.colorScheme.secondary
        .copy(alpha = if (available) 1f else 0.38f)
    FocusableRow(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        onButtonY = if (showSave) onManageSaved else null,
        onButtonX = if (showSave) onToggleFavorite else null,
    ) {
        Text(
            track.trackNumber?.toString() ?: "-",
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryColor,
            modifier = Modifier.width(28.dp),
        )
        Text(
            track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showSave) {
            SaveTrackButton(
                isFavorite = favorite,
                onToggleFavorite = onToggleFavorite,
                onOpenCrates = onManageSaved,
            )
        }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            when {
                downloading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                downloaded -> Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = "Downloaded",
                    tint = Brass,
                    modifier = Modifier.size(18.dp),
                )
                available -> IconButton(onClick = onDownload, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = "Download track",
                        tint = MutedText,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Text(
            formatDuration(track.durationSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryColor,
        )
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

private fun formatDuration(seconds: Double?): String {
    if (seconds == null) return "--:--"
    val total = seconds.toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
