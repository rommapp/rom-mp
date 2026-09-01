package com.rommapp.rommp.ui.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rommapp.rommp.data.playback.RepeatMode
import com.rommapp.rommp.ui.input.FocusableIconButton
import com.rommapp.rommp.ui.input.cursorActivate
import com.rommapp.rommp.ui.input.cursorFocus
import com.rommapp.rommp.ui.input.cursorSection
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.Cream
import com.rommapp.rommp.ui.theme.LedFavorite
import com.rommapp.rommp.ui.theme.LedRepeat
import com.rommapp.rommp.ui.theme.LedShuffle
import com.rommapp.rommp.ui.theme.MutedText

/** Transport + function deck with status LEDs. Kept compact so the record dominates. */
@Composable
fun ControlDeck(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeatMode: RepeatMode,
    locked: Boolean,
    queueOpen: Boolean,
    showSave: Boolean,
    isFavorite: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenCrates: () -> Unit,
    onWall: () -> Unit,
    modifier: Modifier = Modifier,
    deckFocus: FocusRequester? = null,
    playPauseFocus: FocusRequester? = null,
) {
    Column(
        modifier.fillMaxWidth().cursorSection(deckFocus),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FocusableIconButton(onClick = onPrev) {
                Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(30.dp), tint = Cream)
            }
            FocusableIconButton(
                onClick = onPlayPause,
                modifier = if (playPauseFocus != null) Modifier.focusRequester(playPauseFocus) else Modifier,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(44.dp),
                    tint = Cream,
                )
            }
            FocusableIconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(30.dp), tint = Cream)
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (showSave) {
                DeckButton(
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (isFavorite) "Unfavorite (hold: crates)" else "Favorite (hold: crates)",
                    active = isFavorite,
                    ledColor = LedFavorite,
                    onClick = onToggleFavorite,
                    onLongClick = onOpenCrates,
                )
            }
            DeckButton(Icons.Filled.Shuffle, "Shuffle", shuffle, LedShuffle, onToggleShuffle)
            DeckButton(
                icon = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                label = "Repeat",
                active = repeatMode != RepeatMode.OFF,
                ledColor = LedRepeat,
                onClick = onToggleRepeat,
            )
            DeckButton(
                icon = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                label = "Lock",
                active = locked,
                ledColor = null,
                onClick = onToggleLock,
            )
            DeckButton(Icons.AutoMirrored.Filled.QueueMusic, "Queue", queueOpen, null, onToggleQueue)
            DeckButton(Icons.Filled.GridView, "Wall", active = false, ledColor = null, onClick = onWall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    ledColor: Color?,
    onClick: () -> Unit,
    ledActive: Boolean = active,
    onLongClick: (() -> Unit)? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (ledColor != null) Led(ledActive, ledColor) else Box(Modifier.size(14.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .cursorFocus(CircleShape)
                .cursorActivate(onClick = onClick, onButtonY = onLongClick)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = if (active) Brass else MutedText,
            )
        }
    }
}
