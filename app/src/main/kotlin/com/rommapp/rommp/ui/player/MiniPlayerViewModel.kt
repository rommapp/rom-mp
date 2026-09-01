package com.rommapp.rommp.ui.player

import androidx.lifecycle.ViewModel
import com.rommapp.rommp.data.playback.PlaybackManager
import com.rommapp.rommp.data.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
) : ViewModel() {
    val state: StateFlow<PlaybackState> = playbackManager.state
    fun togglePlayPause() = playbackManager.togglePlayPause()
}
