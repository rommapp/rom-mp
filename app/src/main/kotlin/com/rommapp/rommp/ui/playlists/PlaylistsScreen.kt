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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rommapp.rommp.data.music.Playlist
import com.rommapp.rommp.ui.input.FocusHalo
import com.rommapp.rommp.ui.input.FocusableIconButton
import com.rommapp.rommp.ui.input.FocusableRow
import com.rommapp.rommp.ui.input.cursorFocus
import com.rommapp.rommp.ui.input.cursorSection
import com.rommapp.rommp.ui.player.MiniPlayer
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.Cream
import com.rommapp.rommp.ui.theme.Felt
import com.rommapp.rommp.ui.theme.LedLocked
import com.rommapp.rommp.ui.theme.MutedText

private val CrateBackground = Color(0xFF17110C)
private val HeaderBackground = Color(0xFF241B14)
private val SleeveBlack = Color(0xFF1B1510)
private val RecordBlack = Color(0xFF111114)

/** The crate: hand-assembled collections, flipped through like white-label records. */
@Composable
fun PlaylistsScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canWrite by viewModel.canWrite.collectAsStateWithLifecycle()
    val listFocus = remember { FocusRequester() }
    val headerFocus = remember { FocusRequester() }
    val hasPlaylists = state.playlists.isNotEmpty()
    // Primary content holds the cursor: focus the list when rows appear, and
    // again when the create sheet closes (the list section's focusRestorer
    // puts the cursor back on the last focused row). With no rows (loading,
    // error, empty) the header section is the fallback, so the cursor is
    // always visibly seeded.
    LaunchedEffect(state.createOpen, hasPlaylists) {
        if (state.createOpen) return@LaunchedEffect
        // Rows compose during the next layout pass; focus after they exist.
        withFrameNanos { }
        runCatching { if (hasPlaylists) listFocus.requestFocus() else headerFocus.requestFocus() }
    }
    // Refetch on every entry (including back-nav), so counts reflect
    // add-to-crate mutations made on other screens.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Box(
        Modifier
            .fillMaxSize()
            // On the sheet-and-list common ancestor, so B still lands here when
            // the sheet's text field holds focus. The sheet closes first, like
            // scrim tap-out; otherwise B navigates back. B is the ONLY key a
            // screen root may consume.
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.ButtonB) {
                    if (state.createOpen) viewModel.setCreateOpen(false) else onBack()
                    true
                } else {
                    false
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(CrateBackground),
        ) {
            CrateHeader(
                showAdd = canWrite,
                headerFocus = headerFocus,
                onBack = onBack,
                onAdd = { viewModel.setCreateOpen(true) },
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
                    state.playlists.isEmpty() -> Center { Text("No crates yet") }
                    else -> LazyColumn(Modifier.fillMaxSize().cursorSection(listFocus)) {
                        items(state.playlists, key = { it.id }) { playlist ->
                            PlaylistRow(playlist, onClick = { onOpenPlaylist(playlist) })
                        }
                    }
                }
            }
            MiniPlayer(onOpen = onOpenPlayer)
        }
        if (state.createOpen) {
            NewCrateSheet(
                error = state.createError,
                onCreate = viewModel::createPlaylist,
                onDismiss = { viewModel.setCreateOpen(false) },
            )
        }
    }
}

@Composable
private fun CrateHeader(
    showAdd: Boolean,
    headerFocus: FocusRequester,
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(HeaderBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .cursorSection(headerFocus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FocusableIconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        // A drawn mini sleeve holding a brass crate emblem.
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)).background(SleeveBlack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = Brass, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "Crate",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (showAdd) {
            FocusableIconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "New crate", tint = Cream)
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, onClick: () -> Unit) {
    // One focus stop per row: A opens the crate.
    FocusableRow(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        WhiteLabelRecord()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                playlistMeta(playlist),
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun playlistMeta(playlist: Playlist): String = buildString {
    append("${playlist.trackCount} TRACKS")
    when {
        !playlist.isOwned -> append(" · ${playlist.ownerUsername}")
        playlist.isPublic -> append(" · PUBLIC")
    }
}

/** A blank white-label record: black disc, cream label, brass spindle pin. */
@Composable
private fun WhiteLabelRecord() {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(RecordBlack),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(Cream))
        Box(Modifier.size(4.dp).clip(CircleShape).background(Brass))
    }
}

@Composable
private fun NewCrateSheet(
    error: String?,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val sheetFocus = remember { FocusRequester() }
    // Enter the sheet section once composed; the text field is its first stop.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { sheetFocus.requestFocus() }
    }
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
                .cursorSection(sheetFocus),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("NEW CRATE", style = MaterialTheme.typography.labelSmall, color = MutedText)
                Spacer(Modifier.height(10.dp))
                // Ring drawn around the field, not on it: the field's own fill
                // would paint over a border applied to the field node itself.
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
                    FocusHalo(onActivate = { onCreate(name) }, enabled = name.isNotBlank()) {
                        TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
