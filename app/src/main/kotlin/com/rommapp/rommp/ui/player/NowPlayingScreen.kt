package com.rommapp.rommp.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rommapp.rommp.data.playback.PlaybackState
import com.rommapp.rommp.ui.input.cursorSection
import com.rommapp.rommp.ui.playlists.AddToCrateSheet
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.Felt
import com.rommapp.rommp.ui.theme.FeltRaised
import com.rommapp.rommp.ui.theme.LedLocked
import com.rommapp.rommp.ui.theme.MutedText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PlayerBackground = Color(0xFF16130F)
private const val DEGREES_PER_SECOND = 150f

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// setShowWhenLocked is API 27+; API 26 uses the equivalent (now deprecated) window flag.
@Suppress("DEPRECATION")
private fun Activity.setShowOverKeyguard(show: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(show)
    } else if (show) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
    }
}

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onReturnToWall: () -> Unit,
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // Occlude the keyguard while the player is on screen, so rotating the device
    // shut keeps showing the player instead of the system lock screen. Scoped to
    // this view: reverted on dispose so lock behaves normally everywhere else. The
    // same window also arms the Activity's rotate-shut (F12) -> lock handling.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.setShowOverKeyguard(true)
        viewModel.setPlayerVisible(true)
        onDispose {
            activity?.setShowOverKeyguard(false)
            viewModel.setPlayerVisible(false)
        }
    }

    // Rotate-shut engages the lock, rotate-open releases it (same state as the
    // lock button), both scoped to this view.
    LaunchedEffect(Unit) {
        launch { viewModel.lockRequests.collect { viewModel.lock() } }
        launch { viewModel.unlockRequests.collect { viewModel.unlock() } }
    }

    val rootFocus = remember { FocusRequester() }
    val deckFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    var focusInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(ui.addToCrateOpen, ui.queueOpen, ui.locked) {
        if (ui.addToCrateOpen || ui.queueOpen) return@LaunchedEffect
        withFrameNanos { }
        runCatching {
            when {
                ui.locked -> rootFocus.requestFocus()
                !focusInitialized -> playFocus.requestFocus()
                else -> deckFocus.requestFocus()
            }
        }
        focusInitialized = true
    }

    var rotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            rotation = (rotation + (now - last) / 1_000_000_000f * DEGREES_PER_SECOND) % 360f
            last = now
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(PlayerBackground)
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    ui.queueOpen && state.queue.isEmpty() &&
                    event.key != Key.ButtonB
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (ui.addToCrateOpen) {
                    return@onKeyEvent when (event.key) {
                        Key.ButtonB -> { viewModel.closeAddToCrate(); true }
                        Key.ButtonL1, Key.ButtonR1,
                        Key.ButtonA, Key.DirectionCenter, Key.Enter,
                        -> true
                        else -> false
                    }
                }
                if (ui.queueOpen) {
                    return@onKeyEvent when (event.key) {
                        Key.ButtonB -> { viewModel.closeQueue(); true }
                        Key.ButtonL1, Key.ButtonR1,
                        Key.ButtonA, Key.DirectionCenter, Key.Enter,
                        -> true
                        else -> false
                    }
                }
                if (ui.locked) {
                    return@onKeyEvent when (event.key) {
                        Key.ButtonL1, Key.DirectionLeft -> { viewModel.previous(); true }
                        Key.ButtonR1, Key.DirectionRight -> { viewModel.next(); true }
                        Key.ButtonA, Key.DirectionCenter, Key.Enter -> {
                            viewModel.togglePlayPause(); true
                        }
                        Key.ButtonB -> { onBack(); true }
                        Key.DirectionUp, Key.DirectionDown -> true
                        else -> false
                    }
                }
                when (event.key) {
                    Key.ButtonL1 -> { viewModel.previous(); true }
                    Key.ButtonR1 -> { viewModel.next(); true }
                    Key.ButtonB -> { onBack(); true }
                    Key.DirectionLeft ->
                        if (rootFocused) { viewModel.previous(); true } else false
                    Key.DirectionRight ->
                        if (rootFocused) { viewModel.next(); true } else false
                    Key.ButtonA, Key.DirectionCenter, Key.Enter ->
                        if (rootFocused) { viewModel.togglePlayPause(); true } else false
                    else -> false
                }
            },
    ) {
        val ratio = maxWidth.value / maxHeight.value
        when {
            ratio > 1.22f -> LandscapeLayout(state, ui, rotation, viewModel, onReturnToWall, deckFocus, playFocus)
            ratio < 0.82f -> PortraitLayout(state, ui, rotation, viewModel, onReturnToWall, deckFocus, playFocus)
            else -> SquareLayout(state, ui, rotation, viewModel, onReturnToWall, deckFocus, playFocus)
        }

        if (ui.queueOpen) {
            QueueOverlay(state, viewModel)
        }
        if (ui.addToCrateOpen) {
            state.currentTrack?.let { track ->
                AddToCrateSheet(track = track, onDismiss = viewModel::closeAddToCrate)
            }
        }
        if (ui.locked) {
            LockOverlay(onUnlock = viewModel::unlock)
        }
    }
}

@Composable
private fun SquareLayout(
    state: PlaybackState,
    ui: PlayerUiState,
    rotation: Float,
    viewModel: NowPlayingViewModel,
    onReturnToWall: () -> Unit,
    deckFocus: FocusRequester,
    playPauseFocus: FocusRequester,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RecordArea(state.currentTrack?.albumCoverUrl, rotation, onReturnToWall, Modifier.weight(1f).fillMaxWidth())
        PlayerControls(state, ui, viewModel, onReturnToWall, deckFocus, playPauseFocus)
    }
}

@Composable
private fun PortraitLayout(
    state: PlaybackState,
    ui: PlayerUiState,
    rotation: Float,
    viewModel: NowPlayingViewModel,
    onReturnToWall: () -> Unit,
    deckFocus: FocusRequester,
    playPauseFocus: FocusRequester,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        RecordArea(state.currentTrack?.albumCoverUrl, rotation, onReturnToWall, Modifier.weight(0.5f).fillMaxWidth())
        PlayerControls(state, ui, viewModel, onReturnToWall, deckFocus, playPauseFocus)
        QueuePanel(
            queue = state.queue,
            currentIndex = state.currentIndex,
            onPlay = viewModel::playIndex,
            header = "Up Next",
            modifier = Modifier.weight(0.5f).fillMaxWidth(),
        )
    }
}

@Composable
private fun LandscapeLayout(
    state: PlaybackState,
    ui: PlayerUiState,
    rotation: Float,
    viewModel: NowPlayingViewModel,
    onReturnToWall: () -> Unit,
    deckFocus: FocusRequester,
    playPauseFocus: FocusRequester,
) {
    Row(Modifier.fillMaxSize().padding(12.dp)) {
        Column(
            Modifier.weight(0.56f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RecordArea(state.currentTrack?.albumCoverUrl, rotation, onReturnToWall, Modifier.weight(1f).fillMaxWidth())
            PlayerControls(state, ui, viewModel, onReturnToWall, deckFocus, playPauseFocus)
        }
        QueuePanel(
            queue = state.queue,
            currentIndex = state.currentIndex,
            onPlay = viewModel::playIndex,
            header = "Up Next",
            modifier = Modifier.weight(0.44f).fillMaxHeight().padding(start = 8.dp),
        )
    }
}

@Composable
private fun RecordArea(
    coverUrl: String?,
    rotation: Float,
    onReturnToWall: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier.pointerInput(Unit) {
            var total = 0f
            detectVerticalDragGestures(
                onDragEnd = {
                    if (total > 160f) onReturnToWall()
                    total = 0f
                },
                onVerticalDrag = { _, delta -> total += delta },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Vinyl(coverUrl, rotation, Modifier.fillMaxHeight(0.98f).aspectRatio(1f))
    }
}

@Composable
private fun PlayerControls(
    state: PlaybackState,
    ui: PlayerUiState,
    viewModel: NowPlayingViewModel,
    onReturnToWall: () -> Unit,
    deckFocus: FocusRequester,
    playPauseFocus: FocusRequester,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Title stays; when locked, the remaining time appears next to it, faded.
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.currentTrack?.title ?: "Nothing playing",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ui.locked && state.durationMs > 0) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "-" + formatMs((state.durationMs - state.positionMs).coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MutedText.copy(alpha = 0.55f),
                )
            }
        }
        // Progress + transport + deck collapse away when locked, freeing space for the disc.
        AnimatedVisibility(visible = !ui.locked) {
            val canFavorite by viewModel.canFavorite.collectAsStateWithLifecycle()
            val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                SeekBar(state.positionMs, state.durationMs, viewModel::seekTo)
                ControlDeck(
                    isPlaying = state.isPlaying,
                    shuffle = state.shuffle,
                    repeatMode = state.repeatMode,
                    locked = ui.locked,
                    queueOpen = ui.queueOpen,
                    showSave = canFavorite && state.hasContent,
                    isFavorite = isFavorite,
                    onPrev = viewModel::previous,
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onToggleRepeat = viewModel::cycleRepeat,
                    onToggleLock = viewModel::toggleLock,
                    onToggleQueue = viewModel::toggleQueue,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onOpenCrates = viewModel::openAddToCrate,
                    onWall = onReturnToWall,
                    deckFocus = deckFocus,
                    playPauseFocus = playPauseFocus,
                )
                ui.error?.let { message ->
                    LaunchedEffect(message) {
                        delay(3_000)
                        viewModel.clearError()
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = LedLocked,
                    )
                }
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val max = durationMs.toFloat().coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatMs(positionMs), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(0f, max),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { onSeek(dragValue.toLong()); dragging = false },
            valueRange = 0f..max,
            colors = SliderDefaults.colors(
                thumbColor = Brass,
                activeTrackColor = Brass,
                inactiveTrackColor = FeltRaised,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .focusProperties { canFocus = false },
        )
        Text(formatMs(durationMs), style = MaterialTheme.typography.labelSmall)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun QueueOverlay(state: PlaybackState, viewModel: NowPlayingViewModel) {
    val listFocus = remember { FocusRequester() }
    LaunchedEffect(state.queue.isNotEmpty()) {
        if (state.queue.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        runCatching { listFocus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { viewModel.closeQueue() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = Felt,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .pointerInput(Unit) { detectTapGestures { } }
                .focusProperties { exit = { FocusRequester.Cancel } }
                .cursorSection(),
        ) {
            QueuePanel(
                queue = state.queue,
                currentIndex = state.currentIndex,
                onPlay = { viewModel.playIndex(it); viewModel.closeQueue() },
                header = "Up Next",
                listFocus = listFocus,
            )
        }
    }
}

@Composable
private fun LockOverlay(onUnlock: () -> Unit) {
    // Full-screen blocker: swallow every pointer event so nothing beneath is touchable.
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    )
    // The one exception: a deliberate tap-to-unlock chip, sitting where the faded
    // control deck was.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier
                .padding(bottom = 44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(FeltRaised)
                .pointerInput(Unit) { detectTapGestures { onUnlock() } }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Led(active = true, color = LedLocked)
            Spacer(Modifier.width(8.dp))
            Text(
                "LOCKED · tap to unlock",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
        }
    }
}

internal fun formatMs(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}
