package com.rommapp.rommp.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Fences dpad focus inside a sheet: moves between the sheet's own focusables
 * stay native, while any move that would land on the screen behind the scrim
 * is cancelled. Key events are untouched, so B keeps bubbling to the screen
 * root. Apply to the sheet surface, before any `cursorSection`.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
internal fun Modifier.sheetFocusTrap(): Modifier =
    focusProperties { exit = { FocusRequester.Cancel } }.focusGroup()
