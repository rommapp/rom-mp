package com.rommapp.rommp.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

/**
 * Halves the touch slop for the wrapped content. The RG Rotate's panel reports
 * short scroll flicks that stay under the stock slop, so list drags land as
 * taps on row clickables; a tighter slop lets the scroll container claim the
 * gesture (and cancel the row press) sooner.
 */
@Composable
fun TightTouchSlop(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val tight = remember(base) {
        object : ViewConfiguration by base {
            override val touchSlop: Float get() = base.touchSlop / 2f
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides tight, content = content)
}
