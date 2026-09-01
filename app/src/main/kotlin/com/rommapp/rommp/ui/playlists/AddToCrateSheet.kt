package com.rommapp.rommp.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rommapp.rommp.data.music.Track
import com.rommapp.rommp.ui.input.cursorSection
import com.rommapp.rommp.ui.input.cursorTarget
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.Cream
import com.rommapp.rommp.ui.theme.Felt
import com.rommapp.rommp.ui.theme.LedLocked
import com.rommapp.rommp.ui.theme.MutedText

/**
 * The app's single secondary-action overlay: a hand-rolled bottom sheet that
 * files [track] into a crate (playlist), Favorites, or a freshly created crate.
 * Crates already holding the track show a check; tapping them removes it.
 *
 * Controller grammar: rows are native cursor stops (FocusKit); the first row
 * takes focus on open, A (or dpad center) activates, and B bubbles to the host
 * screen, which closes the sheet. Focus search is fenced inside the sheet, so
 * the screen behind the scrim stays unreachable while it is open.
 */
@Composable
fun AddToCrateSheet(
    track: Track,
    onDismiss: () -> Unit,
    viewModel: AddToCrateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val crates by viewModel.crates.collectAsStateWithLifecycle()
    val membership by viewModel.membership.collectAsStateWithLifecycle()
    val favoriteOverrides by viewModel.favoriteOverrides.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    // Parks focus while no rows exist, so the fenced sheet still owns the dpad.
    val holdFocus = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { viewModel.open() }
    // Focus the rows when they exist and the create field is closed; the field
    // requests its own focus while it is up.
    LaunchedEffect(creating, state.loading) {
        when {
            creating -> Unit
            state.loading -> runCatching { holdFocus.requestFocus() }
            else -> {
                // Rows compose during the next layout pass; focus after they exist.
                withFrameNanos { }
                runCatching { listFocus.requestFocus() }
            }
        }
    }

    val checkedIds = membership[track.romFileId].orEmpty()

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
                .focusRequester(holdFocus)
                .focusable(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("ADD TO CRATE", style = MaterialTheme.typography.labelSmall, color = MutedText)
                Spacer(Modifier.height(4.dp))
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = LedLocked)
                    Spacer(Modifier.height(6.dp))
                }
                if (state.loading) {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val favorite = favoriteOverrides[track.romFileId] ?: track.isFavorite
                    LazyColumn(Modifier.heightIn(max = 320.dp).cursorSection(listFocus)) {
                        item(key = "favorites") {
                            SheetRow(
                                icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                iconTint = if (favorite) Brass else MutedText,
                                title = "Favorites",
                                enabled = true,
                                onClick = { viewModel.toggleFavorite(track) },
                                trailing = {},
                            )
                        }
                        item(key = "new") {
                            if (creating) {
                                NewCrateField(
                                    name = newName,
                                    onNameChange = { newName = it },
                                    onCreate = {
                                        viewModel.createAndAdd(newName, track)
                                        creating = false
                                        newName = ""
                                    },
                                )
                            } else {
                                SheetRow(
                                    icon = Icons.Filled.Add,
                                    iconTint = MutedText,
                                    title = "New crate...",
                                    enabled = true,
                                    onClick = { creating = true },
                                    trailing = {},
                                )
                            }
                        }
                        itemsIndexed(crates, key = { _, playlist -> playlist.id }) { _, playlist ->
                            val added = playlist.id in checkedIds
                            SheetRow(
                                icon = Icons.Filled.Album,
                                iconTint = MutedText,
                                title = playlist.name,
                                subtitle = "${playlist.trackCount} TRACKS",
                                enabled = true,
                                onClick = { viewModel.toggleCrate(playlist, track) },
                                trailing = {
                                    if (added) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "In crate",
                                            tint = Brass,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    // One cursor stop per row; disabled rows stay focusable with A as a no-op.
    Row(
        Modifier
            .fillMaxWidth()
            .cursorTarget(onClick = onClick, enabled = enabled)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint.copy(alpha = contentAlpha),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = Cream.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText.copy(alpha = contentAlpha),
                )
            }
        }
        trailing()
    }
}

@Composable
private fun NewCrateField(name: String, onNameChange: (String) -> Unit, onCreate: () -> Unit) {
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            // The field owns the dpad while it is up: directions are eaten in
            // the preview phase, before the field can turn them into focus
            // moves toward the crate rows behind it.
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && when (event.key) {
                    Key.DirectionUp, Key.DirectionDown,
                    Key.DirectionLeft, Key.DirectionRight,
                    -> true
                    else -> false
                }
            }
            // Activation keys the field lets bubble stop here instead of
            // reaching a row; typed-in Enter still fires the field's onDone.
            .onKeyEvent { event ->
                event.type == KeyEventType.KeyDown && when (event.key) {
                    Key.ButtonA, Key.DirectionCenter, Key.Enter -> true
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            placeholder = { Text("Crate name") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate() }),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color(0x33000000),
                focusedContainerColor = Color(0x33000000),
            ),
            modifier = Modifier.weight(1f).focusRequester(fieldFocus),
        )
        TextButton(onClick = onCreate, enabled = name.isNotBlank()) { Text("Create") }
    }
}
