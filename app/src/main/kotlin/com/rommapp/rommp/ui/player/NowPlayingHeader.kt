package com.rommapp.rommp.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.rommapp.rommp.ui.input.cursorTarget
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.BrassGlow
import com.rommapp.rommp.ui.theme.Cream
import com.rommapp.rommp.ui.theme.MutedText
import com.rommapp.rommp.ui.theme.VinylBlack

private val HeaderOverlay = Color(0xF01C1811)

/**
 * Now-playing banner across the top: a spinning cover on the left, track + game
 * on the right, and a slim progress meter with a glowing position dot along the
 * bottom edge. Tap to open the player. Hidden when nothing is loaded.
 */
@Composable
fun NowPlayingHeader(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val track = state.currentTrack ?: return

    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            rotation = (rotation + (now - last) / 1_000_000_000f * 60f) % 360f
            last = now
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(HeaderOverlay)
            .cursorTarget(
                onClick = onOpen,
                shape = RectangleShape,
                onButtonX = viewModel::togglePlayPause,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpinningCover(track.albumCoverUrl, rotation)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Cream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.gameName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = viewModel::togglePlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = Cream,
                )
            }
        }
        ProgressMeter(state.positionMs, state.durationMs)
    }
}

@Composable
private fun SpinningCover(coverUrl: String?, rotation: Float) {
    Box(Modifier.size(46.dp).rotate(rotation), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(VinylBlack))
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(0.66f).clip(CircleShape),
            )
        }
        Box(Modifier.size(3.dp).clip(CircleShape).background(Brass))
    }
}

@Composable
private fun ProgressMeter(positionMs: Long, durationMs: Long) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val y = size.height / 2f
        val stroke = 2.dp.toPx()
        drawLine(Color(0xFF3A342B), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        val dotX = size.width * fraction
        if (fraction > 0f) {
            drawLine(Brass, Offset(0f, y), Offset(dotX, y), stroke, StrokeCap.Round)
        }
        drawCircle(BrassGlow.copy(alpha = 0.35f), radius = 7.dp.toPx(), center = Offset(dotX, y))
        drawCircle(BrassGlow, radius = 3.5.dp.toPx(), center = Offset(dotX, y))
    }
}
