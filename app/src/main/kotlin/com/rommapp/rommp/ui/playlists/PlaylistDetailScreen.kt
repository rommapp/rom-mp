package com.rommapp.rommp.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
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
import com.rommapp.rommp.ui.input.cursorFocus
import com.rommapp.rommp.ui.input.cursorSection
import com.rommapp.rommp.ui.input.cursorTarget
import com.rommapp.rommp.ui.player.MiniPlayer
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.Cream
import com.rommapp.rommp.ui.theme.Felt
import com.rommapp.rommp.ui.theme.LedLocked
import com.rommapp.rommp.ui.theme.MutedText
import kotlinx.coroutines.delay

private val CrateBackground = Color(0xFF17110C)
private val HeaderBackground = Color(0xFF241B14)
private val SleeveBlack = Color(0xFF1B1510)
private val RecordBlack = Color(0xFF111114)

/** One crate's records, in crate order. Other users' public crates are read-only. */
@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val canWrite by viewModel.canWrite.collectAsStateWithLifecycle()
    val listFocus = remember { FocusRequester() }
    val headerFocus = remember { FocusRequester() }
    val sheetOpen = state.trackActionsIndex != null || state.renameOpen || state.deleteConfirmOpen
    val hasTracks = state.tracks.isNotEmpty()
    // Primary content holds the cursor: focus the list when tracks appear, and
    // again when any sheet closes (the list section's focusRestorer puts the
    // cursor back on the exact row that opened it). With no rows (loading,
    // error, empty) the header section is the fallback, so the cursor is
    // always visibly seeded.
    LaunchedEffect(sheetOpen, hasTracks) {
        if (sheetOpen) return@LaunchedEffect
        // Rows compose during the next layout pass; focus after they exist.
        withFrameNanos { }
        runCatching { if (hasTracks) listFocus.requestFocus() else headerFocus.requestFocus() }
    }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    // Refetch on every entry (including back-nav from the player), so the
    // list reflects add-to-crate mutations made on other screens.
    LaunchedEffect(Unit) { viewModel.refresh() }

    val canEdit = state.playlist?.isOwned == true && canWrite

    Box(
        Modifier
            .fillMaxSize()
            // On the sheet-and-list common ancestor, so B still lands here when
            // a sheet's text field holds focus. Sheets close first, like scrim
            // tap-out; otherwise B navigates back. B is the ONLY key a screen
            // root may consume.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.ButtonB) {
                    return@onKeyEvent false
                }
                when {
                    state.trackActionsIndex != null -> viewModel.closeTrackActions()
                    state.renameOpen -> viewModel.setRenameOpen(false)
                    state.deleteConfirmOpen -> viewModel.setDeleteConfirmOpen(false)
                    else -> onBack()
                }
                true
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(CrateBackground),
        ) {
            CrateDetailHeader(
                name = state.playlist?.name ?: "Crate",
                ownerUsername = state.playlist?.takeIf { !it.isOwned }?.ownerUsername,
                trackCount = state.tracks.size,
                canEdit = canEdit,
                headerFocus = headerFocus,
                onBack = onBack,
                onPlayAll = { viewModel.playAll(); onOpenPlayer() },
                onRename = { viewModel.setRenameOpen(true) },
                onDelete = { viewModel.setDeleteConfirmOpen(true) },
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
                    state.tracks.isEmpty() -> Center { Text("This crate is empty") }
                    else -> CrateTrackList(
                        tracks = state.tracks,
                        playingId = playback.currentTrack?.romFileId,
                        canEdit = canEdit,
                        listFocus = listFocus,
                        onPlay = { index -> viewModel.playTrack(index); onOpenPlayer() },
                        onOpenActions = viewModel::openTrackActions,
                    )
                }
            }
            state.actionError?.let { message ->
                LaunchedEffect(message) {
                    delay(3_000)
                    viewModel.clearActionError()
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
        state.trackActionsIndex?.let { index ->
            state.tracks.getOrNull(index)?.let { track ->
                TrackActionsSheet(
                    track = track,
                    canMoveUp = index > 0,
                    canMoveDown = index < state.tracks.lastIndex,
                    onMoveUp = { viewModel.moveTrack(index, -1) },
                    onMoveDown = { viewModel.moveTrack(index, +1) },
                    onRemove = { viewModel.removeTrack(index) },
                    onDismiss = viewModel::closeTrackActions,
                )
            }
        }
        if (state.renameOpen) {
            RenameCrateSheet(
                initialName = state.playlist?.name.orEmpty(),
                error = state.renameError,
                onRename = viewModel::rename,
                onDismiss = { viewModel.setRenameOpen(false) },
            )
        }
        if (state.deleteConfirmOpen) {
            DeleteCrateSheet(
                name = state.playlist?.name.orEmpty(),
                onDelete = viewModel::deletePlaylist,
                onDismiss = { viewModel.setDeleteConfirmOpen(false) },
            )
        }
    }
}

@Composable
private fun CrateDetailHeader(
    name: String,
    ownerUsername: String?,
    trackCount: Int,
    canEdit: Boolean,
    headerFocus: FocusRequester,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
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
            // A drawn mini sleeve with the crate's white-label record peeking out.
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(SleeveBlack),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(RecordBlack),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(19.dp).clip(CircleShape).background(Cream))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(Brass))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (ownerUsername != null) {
                    Text(
                        "by $ownerUsername",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                    )
                }
            }
            if (canEdit) {
                FocusableIconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = "Rename crate", tint = Cream)
                }
                FocusableIconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete crate", tint = Cream)
                }
            }
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
private fun CrateTrackList(
    tracks: List<Track>,
    playingId: Int?,
    canEdit: Boolean,
    listFocus: FocusRequester,
    onPlay: (Int) -> Unit,
    onOpenActions: (Int) -> Unit,
) {
    // Keyed positionally: romFileId churns on rescans and crates span games.
    TightTouchSlop {
        LazyColumn(Modifier.fillMaxSize().cursorSection(listFocus)) {
            itemsIndexed(tracks) { index, track ->
                CrateTrackRow(
                    position = index + 1,
                    track = track,
                    isPlaying = track.romFileId == playingId,
                    showActions = canEdit,
                    onClick = { onPlay(index) },
                    onOpenActions = { onOpenActions(index) },
                )
            }
        }
    }
}

@Composable
private fun CrateTrackRow(
    position: Int,
    track: Track,
    isPlaying: Boolean,
    showActions: Boolean,
    onClick: () -> Unit,
    onOpenActions: () -> Unit,
) {
    val titleColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    // One focus stop per row: A plays, Y opens the track actions sheet. The
    // inline MoreVert button stays a touch target but is out of the dpad order.
    FocusableRow(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        onButtonY = if (showActions) onOpenActions else null,
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
        if (showActions) {
            IconButton(onClick = onOpenActions, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "Track actions",
                    tint = MutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            formatDuration(track.durationSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
private fun TrackActionsSheet(
    track: Track,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetFocus = remember { FocusRequester() }
    // Enter the sheet section once composed; the first action row is its
    // first stop. Disabled rows stay focusable with A as a guarded no-op.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { sheetFocus.requestFocus() }
    }
    SheetScaffold(
        onDismiss = onDismiss,
        sheetModifier = Modifier.cursorSection(sheetFocus),
    ) {
        Text("TRACK", style = MaterialTheme.typography.labelSmall, color = MutedText)
        Spacer(Modifier.height(4.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = Cream,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        ActionRow(Icons.Filled.KeyboardArrowUp, "Move up", enabled = canMoveUp, onClick = onMoveUp)
        ActionRow(Icons.Filled.KeyboardArrowDown, "Move down", enabled = canMoveDown, onClick = onMoveDown)
        ActionRow(Icons.Filled.PlaylistRemove, "Remove from crate", enabled = true, onClick = onRemove)
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (enabled) Cream else Cream.copy(alpha = 0.38f)
    Row(
        Modifier
            .fillMaxWidth()
            .cursorTarget(onClick = onClick, enabled = enabled)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

@Composable
private fun RenameCrateSheet(
    initialName: String,
    error: String?,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val sheetFocus = remember { FocusRequester() }
    // Enter the sheet section once composed; the text field is its first stop.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { sheetFocus.requestFocus() }
    }
    SheetScaffold(
        onDismiss = onDismiss,
        sheetModifier = Modifier.cursorSection(sheetFocus),
    ) {
        Text("RENAME CRATE", style = MaterialTheme.typography.labelSmall, color = MutedText)
        Spacer(Modifier.height(10.dp))
        // Ring drawn around the field, not on it: the field's own fill would
        // paint over a border applied to the field node itself.
        Box(Modifier.fillMaxWidth().cursorFocus().padding(3.dp)) {
            TextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Crate name") },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0x33000000),
                    focusedContainerColor = Color(0x33000000),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = LedLocked)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FocusHalo(onActivate = onDismiss) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            Spacer(Modifier.weight(1f))
            FocusHalo(onActivate = { onRename(name) }, enabled = name.isNotBlank()) {
                TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text("Rename") }
            }
        }
    }
}

@Composable
private fun DeleteCrateSheet(name: String, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val sheetFocus = remember { FocusRequester() }
    // Enter the sheet section once composed; Cancel is its first stop, so the
    // destructive action always takes a deliberate move.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { sheetFocus.requestFocus() }
    }
    SheetScaffold(
        onDismiss = onDismiss,
        sheetModifier = Modifier.cursorSection(sheetFocus),
    ) {
        Text("DELETE CRATE", style = MaterialTheme.typography.labelSmall, color = MutedText)
        Spacer(Modifier.height(10.dp))
        Text(
            "Break up \"$name\"? Its records stay in your library.",
            style = MaterialTheme.typography.bodyLarge,
            color = Cream,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FocusHalo(onActivate = onDismiss) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            Spacer(Modifier.weight(1f))
            FocusHalo(onActivate = onDelete) {
                TextButton(onClick = onDelete) { Text("Delete", color = LedLocked) }
            }
        }
    }
}

/** The house bottom sheet: full scrim, tap-outside dismiss, Felt surface. */
@Composable
private fun SheetScaffold(
    onDismiss: () -> Unit,
    sheetModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = Felt,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } }
                .sheetFocusTrap()
                .then(sheetModifier),
        ) {
            Column(Modifier.padding(16.dp)) { content() }
        }
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
