package com.rommapp.rommp.ui.favorites

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rommapp.rommp.data.music.Track
import com.rommapp.rommp.ui.TightTouchSlop
import com.rommapp.rommp.ui.input.FocusHalo
import com.rommapp.rommp.ui.input.FocusableIconButton
import com.rommapp.rommp.ui.input.FocusableRow
import com.rommapp.rommp.ui.input.cursorSection
import com.rommapp.rommp.ui.player.MiniPlayer
import com.rommapp.rommp.ui.playlists.AddToCrateSheet
import com.rommapp.rommp.ui.playlists.SaveTrackButton
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.LedLocked
import com.rommapp.rommp.ui.theme.MutedText
import kotlinx.coroutines.delay

private val FavoritesBackground = Color(0xFF17110C)
private val HeaderBackground = Color(0xFF241B14)
private val SleeveBlack = Color(0xFF1B1510)

/** The mixtape at the front of the crate: every liked track across all games. */
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val canFavorite by viewModel.canFavorite.collectAsStateWithLifecycle()
    val favoriteOverrides by viewModel.favoriteOverrides.collectAsStateWithLifecycle()
    var crateTrack by remember { mutableStateOf<Track?>(null) }
    val listFocus = remember { FocusRequester() }
    val headerFocus = remember { FocusRequester() }
    val hasTracks = state.tracks.isNotEmpty()
    // Primary content holds the cursor: focus the list when tracks appear, and
    // again when the crate sheet closes (the list section's focusRestorer puts
    // the cursor back on the exact row that opened it). With no rows (loading,
    // error, empty) the header section is the fallback, so the cursor is
    // always visibly seeded.
    LaunchedEffect(crateTrack, hasTracks) {
        if (crateTrack != null) return@LaunchedEffect
        // Rows compose during the next layout pass; focus after they exist.
        withFrameNanos { }
        runCatching { if (hasTracks) listFocus.requestFocus() else headerFocus.requestFocus() }
    }
    // After a stale-id refetch, re-point the open sheet at the refreshed row.
    LaunchedEffect(state.tracks) {
        crateTrack = crateTrack?.let { current ->
            state.tracks.firstOrNull { it.romFileId == current.romFileId } ?: current
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // On the sheet-and-list common ancestor, so B lands here from the
            // sheet too. The sheet closes first, like scrim tap-out; otherwise
            // B navigates back. B is the ONLY key a screen root may consume.
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
                .background(FavoritesBackground),
        ) {
            FavoritesHeader(
                trackCount = state.tracks.size,
                headerFocus = headerFocus,
                onBack = onBack,
                onPlayAll = { viewModel.playAll(); onOpenPlayer() },
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
                    state.tracks.isEmpty() -> Center { Text("No favorites yet") }
                    else -> FavoritesList(
                        tracks = state.tracks,
                        playingId = playback.currentTrack?.romFileId,
                        showSave = canFavorite,
                        favoriteOverrides = favoriteOverrides,
                        listFocus = listFocus,
                        onPlay = { index -> viewModel.playTrack(index); onOpenPlayer() },
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
private fun FavoritesHeader(
    trackCount: Int,
    headerFocus: FocusRequester,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
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
            // A drawn mini sleeve: cardboard square with a brass heart label.
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(SleeveBlack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, tint = Brass, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Favorites",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusHalo(onActivate = onPlayAll, enabled = trackCount > 0) {
                Button(onClick = onPlayAll, enabled = trackCount > 0) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play All")
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "$trackCount TRACKS",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}

@Composable
private fun FavoritesList(
    tracks: List<Track>,
    playingId: Int?,
    showSave: Boolean,
    favoriteOverrides: Map<Int, Boolean>,
    listFocus: FocusRequester,
    onPlay: (Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onManageSaved: (Track) -> Unit,
) {
    // Keyed positionally: romFileId churns on rescans and favorites span games.
    TightTouchSlop {
        LazyColumn(Modifier.fillMaxSize().cursorSection(listFocus)) {
            itemsIndexed(tracks) { index, track ->
                val favorite = favoriteOverrides[track.romFileId] ?: track.isFavorite
                FavoriteTrackRow(
                    position = index + 1,
                    track = track,
                    isPlaying = track.romFileId == playingId,
                    showSave = showSave,
                    favorite = favorite,
                    onClick = { onPlay(index) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onManageSaved = { onManageSaved(track) },
                )
            }
        }
    }
}

@Composable
private fun FavoriteTrackRow(
    position: Int,
    track: Track,
    isPlaying: Boolean,
    showSave: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onManageSaved: () -> Unit,
) {
    val titleColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    // One focus stop per row: A plays, Y opens the crate sheet, X toggles
    // favorite. The inline save button stays a touch target but is out of the
    // dpad order.
    FocusableRow(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        onButtonY = if (showSave) onManageSaved else null,
        onButtonX = if (showSave) onToggleFavorite else null,
    ) {
        Text(
            position.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(28.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.gameName,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showSave) {
            SaveTrackButton(
                isFavorite = favorite,
                onToggleFavorite = onToggleFavorite,
                onOpenCrates = onManageSaved,
            )
        }
        Text(
            formatDuration(track.durationSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
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
