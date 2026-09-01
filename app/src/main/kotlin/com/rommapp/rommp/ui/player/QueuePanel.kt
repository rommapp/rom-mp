package com.rommapp.rommp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rommapp.rommp.data.music.Track
import com.rommapp.rommp.ui.input.FocusableRow
import com.rommapp.rommp.ui.input.cursorSection

/**
 * The current play queue. Fills the surplus space on non-square layouts and backs
 * the "Queue" control. The playing track is highlighted; tapping jumps to it.
 */
@Composable
fun QueuePanel(
    queue: List<Track>,
    currentIndex: Int,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    header: String? = null,
    listFocus: FocusRequester? = null,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex in queue.indices) listState.animateScrollToItem(currentIndex)
    }
    Column(modifier) {
        if (header != null) {
            Text(
                header,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().cursorSection(listFocus)) {
            itemsIndexed(queue, key = { _, t -> t.romFileId }) { index, track ->
                QueueRow(
                    track = track,
                    position = index + 1,
                    isCurrent = index == currentIndex,
                    onClick = { onPlay(index) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(track: Track, position: Int, isCurrent: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val rowBackground = if (isCurrent) accent.copy(alpha = 0.12f) else Color.Transparent
    FocusableRow(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().background(rowBackground),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            position.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) accent else MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(28.dp),
        )
        Text(
            track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMs((track.durationSeconds ?: 0.0).toLong() * 1000),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
