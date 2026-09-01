package com.rommapp.rommp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rommapp.rommp.ui.input.cursorTarget
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.Cream
import com.rommapp.rommp.ui.theme.FeltRaised
import com.rommapp.rommp.ui.theme.LedLocked
import com.rommapp.rommp.ui.theme.MutedText
import com.rommapp.rommp.ui.theme.VinylBlack
import kotlinx.coroutines.delay

/**
 * Persistent now-playing bar for the library/album screens: shows the current
 * track and returns to the player on tap. Renders nothing when nothing is loaded.
 */
@Composable
fun MiniPlayer(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return

    Column(
        modifier
            .fillMaxWidth()
            .background(FeltRaised)
            .cursorTarget(
                onClick = onOpen,
                shape = RectangleShape,
                onButtonX = viewModel::togglePlayPause,
            ),
    ) {
        state.error?.let { message ->
            var visible by remember(message) { mutableStateOf(true) }
            LaunchedEffect(message) {
                delay(3_000)
                visible = false
            }
            if (visible) {
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = LedLocked,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(VinylBlack), contentAlignment = Alignment.Center) {
                if (track.albumCoverUrl != null) {
                    AsyncImage(
                        model = track.albumCoverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(0.6f).clip(CircleShape),
                    )
                }
                Box(Modifier.size(4.dp).clip(CircleShape).background(Brass))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Now Playing", style = MaterialTheme.typography.labelSmall, color = MutedText)
            }
            IconButton(onClick = viewModel::togglePlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = Cream,
                )
            }
        }
    }
}
