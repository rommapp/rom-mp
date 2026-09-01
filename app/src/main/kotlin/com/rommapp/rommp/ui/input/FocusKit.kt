package com.rommapp.rommp.ui.input

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.FeltRaised
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FocusKit: the app's single controller-focus vocabulary.
 *
 * The cursor is Compose's native focus, made visible. Hardware dpad drives
 * normal focus search; the kit adds one ring style, one key grammar, and an
 * idle-hide cursor. Every screen follows the same rules:
 *
 * KEY GRAMMAR
 * - A / DirectionCenter / Enter: primary action of the focused element (what a
 *   tap does). Fires immediately, even while the cursor is hidden. On targets
 *   that also have a hold action, A instead fires on release and a long-hold of
 *   A opens that action (the touch long-press equivalent, see below).
 * - Y: the element's touch long-press equivalent (open crates / actions sheet),
 *   as a dedicated key alongside the A long-hold.
 * - X: the element's inline icon affordance (the small button inside a row):
 *   heart-tap (toggle favorite) on track rows, play/pause on the mini player.
 * - B: close-the-topmost-sheet-else-back, handled by the SCREEN ROOT only.
 *   B never moves focus and no element may consume it.
 * - L1/R1: reserved for paging/transport (library shelf, player skip). Never
 *   bound to per-element actions.
 *
 * CURSOR IDLE-HIDE
 * - The ring hides after [CURSOR_IDLE_HIDE_MS] of controller inactivity and on
 *   any touch. Focus POSITION is always retained; hiding is purely visual.
 * - While hidden, the FIRST direction press only reveals the ring (no focus
 *   move) so the user sees where they are. Activation keys (A/B/X/Y, L1/R1)
 *   always act immediately and also reveal the ring.
 * - [CursorHost] (installed once, at RootScreen) owns the timer, the reveal
 *   interception, and the touch watcher, and provides [LocalCursorState].
 *
 * SECTIONS AND INITIAL FOCUS
 * - Each screen region (header cluster, list, mini player, caption bar) is a
 *   [cursorSection]: dpad up/down crosses sections via native focus search,
 *   and re-entering a section restores its last focused child (focusRestorer).
 * - Initial focus goes to PRIMARY CONTENT first (the track list, the shelf),
 *   not the header. Request it through the section's FocusRequester once the
 *   content exists; the same request restores focus after a sheet closes.
 *
 * ROOT KEY HANDLERS
 * - Screen roots may intercept ButtonB only. They must never consume
 *   direction keys, A, X, or Y outside an open overlay, because native focus
 *   traversal and the element grammar need them. Overlays that hold a private
 *   selection (AddToCrateSheet) may swallow directions while open.
 *
 * SINGLE FOCUS TARGET PER ROW
 * - Rows are ONE focus stop; inner buttons stay tappable but are removed from
 *   the dpad order ([cursorTarget] suppresses child focusables). Their actions
 *   ride the row's X/Y keys instead. Do not put text fields inside a
 *   [cursorTarget] subtree.
 */
const val CURSOR_IDLE_HIDE_MS = 4_000L

/** App-wide cursor visibility. Focus position is unaffected by [visible]. */
@Stable
class CursorState {
    var visible by mutableStateOf(true)
        private set

    /** Bumped on every controller input; restarts the idle-hide timer. */
    var inputStamp by mutableLongStateOf(0L)
        private set

    fun show() {
        visible = true
        inputStamp += 1
    }

    fun hide() {
        visible = false
    }
}

val LocalCursorState = staticCompositionLocalOf<CursorState> {
    error("CursorHost must be installed above any FocusKit usage")
}

/**
 * Installs the cursor tracker at the app root: idle-hide timer, reveal-only
 * first direction press, and touch-hides-cursor. Wrap the whole nav tree once.
 */
@Composable
fun CursorHost(content: @Composable () -> Unit) {
    val cursor = remember { CursorState() }

    LaunchedEffect(cursor.visible, cursor.inputStamp) {
        if (cursor.visible) {
            delay(CURSOR_IDLE_HIDE_MS)
            cursor.hide()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            // Preview phase runs root-first, so the reveal press is eaten
            // before any screen or sheet handler can move focus with it.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !isControllerKey(event.key)) {
                    return@onPreviewKeyEvent false
                }
                val wasHidden = !cursor.visible
                cursor.show()
                wasHidden && isDirectionKey(event.key)
            }
            // Initial pass observes without consuming, so touch keeps working.
            .pointerInput(cursor) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.type == PointerType.Touch }) cursor.hide()
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalCursorState provides cursor, content = content)
    }
}

private fun isDirectionKey(key: Key): Boolean = when (key) {
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> true
    else -> false
}

private fun isControllerKey(key: Key): Boolean = when (key) {
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
    Key.ButtonA, Key.ButtonB, Key.ButtonX, Key.ButtonY,
    Key.ButtonL1, Key.ButtonR1, Key.ButtonL2, Key.ButtonR2,
    Key.ButtonThumbLeft, Key.ButtonThumbRight,
    Key.ButtonSelect, Key.ButtonStart, Key.ButtonMode,
    -> true
    else -> false
}

object CursorDefaults {
    /** Rows and sheet entries (matches AddToCrateSheet's selection). */
    val RowShape = RoundedCornerShape(6.dp)

    /** Pill controls (Play All and other filled buttons). */
    val PillShape = RoundedCornerShape(percent = 50)
}

/**
 * The one focus ring: raised felt under a brass hairline. Also used by sheets
 * that keep a hand-rolled selection index (AddToCrateSheet) so a selected row
 * and a focused row read identically.
 */
fun Modifier.cursorRingStyle(shape: Shape): Modifier =
    clip(shape).background(FeltRaised).border(1.dp, Brass, shape)

/**
 * Focus observation + ring for the focus target that FOLLOWS this modifier in
 * the chain (a `clickable`/`focusable`). Renders [cursorRingStyle] while
 * focused and the cursor is visible, and scrolls the element into view on
 * focus so lazy-list rows follow the cursor.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.cursorFocus(shape: Shape = CursorDefaults.RowShape): Modifier {
    val cursor = LocalCursorState.current
    val scope = rememberCoroutineScope()
    val bringIntoView = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val show = focused && cursor.visible
    return this
        .bringIntoViewRequester(bringIntoView)
        .onFocusEvent { state ->
            focused = state.isFocused
            if (state.isFocused) scope.launch { bringIntoView.bringIntoView() }
        }
        .clip(shape)
        .background(if (show) FeltRaised else Color.Transparent)
        .border(1.dp, if (show) Brass else Color.Transparent, shape)
}

/** Per-target confirm-hold bookkeeping so key-repeat and release don't re-fire. */
private class ConfirmHold {
    var job: Job? = null
    var fired = false
}

/**
 * Controller activation for a focused element, per the file-header grammar:
 * A/Center/Enter -> [onClick], Y -> [onButtonY], X -> [onButtonX]. Enter and
 * DirectionCenter are normally consumed by the sibling `clickable` first; the
 * branches here are the fallback for plain-`focusable` targets. Held-key
 * auto-repeats are swallowed without re-firing.
 *
 * When [onButtonY] exists, a long-hold of A also triggers it (the touch
 * long-press equivalent), so gamepad users reach the hold action without the
 * dedicated Y key. Only A is handled here: the sibling `combinedClickable`
 * already gives Enter/DirectionCenter their own keyboard long-press. A short A
 * press then fires [onClick] on release; a hold past the system long-press
 * timeout fires [onButtonY] once.
 */
@Composable
fun Modifier.cursorActivate(
    onClick: () -> Unit,
    onButtonY: (() -> Unit)? = null,
    onButtonX: (() -> Unit)? = null,
    enabled: Boolean = true,
): Modifier {
    val scope = rememberCoroutineScope()
    val holdTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis
    val hold = remember { ConfirmHold() }
    return onKeyEvent { event ->
        if (!enabled) return@onKeyEvent false
        if (event.key == Key.ButtonA && onButtonY != null) {
            when (event.type) {
                KeyEventType.KeyDown -> if (event.nativeKeyEvent.repeatCount == 0) {
                    hold.fired = false
                    hold.job?.cancel()
                    hold.job = scope.launch {
                        delay(holdTimeoutMs)
                        hold.fired = true
                        onButtonY()
                    }
                }
                KeyEventType.KeyUp -> {
                    hold.job?.cancel()
                    hold.job = null
                    if (!hold.fired) onClick()
                    hold.fired = false
                }
            }
            return@onKeyEvent true
        }
        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
        val action = when (event.key) {
            Key.ButtonA, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> onClick
            Key.ButtonY -> onButtonY
            Key.ButtonX -> onButtonX
            else -> null
        } ?: return@onKeyEvent false
        if (event.nativeKeyEvent.repeatCount == 0) action()
        true
    }
}

/**
 * The full single-focus-target treatment for a clickable container: ring +
 * key grammar + tap/long-press + suppression of inner focusables (inner
 * buttons stay tappable but leave the dpad order; bind them to X/Y instead).
 * Touch long-press mirrors the controller's Y action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.cursorTarget(
    onClick: () -> Unit,
    enabled: Boolean = true,
    shape: Shape = CursorDefaults.RowShape,
    onButtonY: (() -> Unit)? = null,
    onButtonX: (() -> Unit)? = null,
): Modifier = this
    .cursorFocus(shape)
    .cursorActivate(onClick = onClick, onButtonY = onButtonY, onButtonX = onButtonX, enabled = enabled)
    .combinedClickable(enabled = enabled, onLongClick = onButtonY, onClick = onClick)
    .focusProperties { canFocus = false }

/**
 * Marks a screen region (header cluster, list, mini player, caption bar) as a
 * focus section: dpad crosses sections predictably and re-entry restores the
 * last focused child. Pass [restorePoint] to also target the section from
 * code: `restorePoint.requestFocus()` enters the section, restoring the saved
 * child (or its first child on first entry) - use it for initial focus and
 * for reclaiming focus after a sheet closes.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
fun Modifier.cursorSection(restorePoint: FocusRequester? = null): Modifier {
    val base = if (restorePoint != null) focusRequester(restorePoint) else this
    return base.focusGroup()
}

/** A dpad-activatable list row: one focus stop, ring included. */
@Composable
fun FocusableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CursorDefaults.RowShape,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onButtonY: (() -> Unit)? = null,
    onButtonX: (() -> Unit)? = null,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .cursorTarget(
                onClick = onClick,
                enabled = enabled,
                shape = shape,
                onButtonY = onButtonY,
                onButtonX = onButtonX,
            )
            .padding(contentPadding),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** A dpad-activatable icon button; drop-in for Material `IconButton`. */
@Composable
fun FocusableIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    onButtonY: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(size).cursorTarget(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            onButtonY = onButtonY,
        ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Focus halo around an already-styled control (e.g. a filled Material Button,
 * whose brass surface would hide a brass border drawn on it). The halo is the
 * single focus stop; the wrapped control keeps handling touch, and A fires
 * [onActivate]. The 3.dp inset leaves a felt gap so the ring stays readable.
 */
@Composable
fun FocusHalo(
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CursorDefaults.PillShape,
    onButtonY: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .cursorFocus(shape)
            .cursorActivate(onClick = onActivate, onButtonY = onButtonY, enabled = enabled)
            .focusable(enabled)
            .focusProperties { canFocus = false }
            .padding(3.dp),
    ) {
        content()
    }
}
