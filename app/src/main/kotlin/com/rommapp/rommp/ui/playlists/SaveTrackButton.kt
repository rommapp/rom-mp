package com.rommapp.rommp.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.MutedText

/**
 * The single save affordance on track rows: tap toggles favorite, hold opens
 * the add-to-crate sheet. Full 48dp touch target: anything smaller lets taps
 * fall through to the row's play click on small screens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SaveTrackButton(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenCrates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onToggleFavorite, onLongClick = onOpenCrates),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription =
                if (isFavorite) "Unfavorite (hold to manage crates)"
                else "Favorite (hold to manage crates)",
            tint = if (isFavorite) Brass else MutedText,
            modifier = Modifier.size(20.dp),
        )
    }
}
