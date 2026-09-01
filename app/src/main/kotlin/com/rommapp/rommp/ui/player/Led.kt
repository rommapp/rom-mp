package com.rommapp.rommp.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rommapp.rommp.ui.theme.LedOff

/**
 * A precision status LED: a small solid core with a soft emissive bloom when lit;
 * dark and desaturated when off (an unlit LED is never a gray outline).
 */
@Composable
fun Led(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(14.dp), contentAlignment = Alignment.Center) {
        if (active) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(color.copy(alpha = 0.28f)))
        }
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) color else LedOff),
        )
    }
}
