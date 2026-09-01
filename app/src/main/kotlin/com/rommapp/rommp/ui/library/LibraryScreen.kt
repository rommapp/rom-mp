package com.rommapp.rommp.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.rommapp.rommp.data.music.GameWithMusic
import com.rommapp.rommp.ui.input.FocusHalo
import com.rommapp.rommp.ui.input.FocusableIconButton
import com.rommapp.rommp.ui.input.LocalCursorState
import com.rommapp.rommp.ui.input.cursorSection
import com.rommapp.rommp.ui.player.NowPlayingHeader
import com.rommapp.rommp.ui.theme.Brass
import com.rommapp.rommp.ui.theme.BrassGlow
import com.rommapp.rommp.ui.theme.Cream
import com.rommapp.rommp.ui.theme.Felt
import com.rommapp.rommp.ui.theme.MutedText
import kotlinx.coroutines.launch

private val WoodWall = Brush.verticalGradient(listOf(Color(0xFF5A4130), Color(0xFF33241A)))
private val ShelfPlank = Brush.verticalGradient(listOf(Color(0xFF7A5636), Color(0xFF4A3320)))
private val SleeveFallback = Color(0xFF241B14)

@Composable
fun LibraryScreen(
    onGameClick: (GameWithMusic) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canSeeFavorites by viewModel.canSeeFavorites.collectAsStateWithLifecycle()
    val pageOffset = if (canSeeFavorites) 1 else 0
    val pagerState = rememberPagerState(pageCount = { state.games.size + pageOffset })
    val shelfFocus = remember { FocusRequester() }
    val captionFocus = remember { FocusRequester() }
    val shelfShown = !state.loading && state.error == null && state.games.isNotEmpty()
    LaunchedEffect(state.filterOpen, state.searchActive, shelfShown) {
        if (state.filterOpen || state.searchActive) return@LaunchedEffect
        withFrameNanos { }
        runCatching { if (shelfShown) shelfFocus.requestFocus() else captionFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(WoodWall)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.ButtonB) {
                    return@onKeyEvent false
                }
                when {
                    state.filterOpen -> { viewModel.setFilterOpen(false); true }
                    state.searchActive -> { viewModel.setSearchActive(false); true }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            NowPlayingHeader(onOpen = onOpenPlayer)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> CenterProgress()
                    state.error != null -> CenterMessage(state.error!!, onRetry = viewModel::retry)
                    state.games.isEmpty() -> CenterMessage(
                        message = if (state.allGames.isEmpty()) "No music found" else "No matches",
                        onRetry = null,
                    )
                    else -> Shelf(state.games, pagerState, canSeeFavorites, shelfFocus, onGameClick, onOpenFavorites)
                }
            }
            CaptionBar(
                title = if (canSeeFavorites && pagerState.currentPage == 0) {
                    "Favorites"
                } else {
                    state.games.getOrNull(pagerState.currentPage - pageOffset)?.name.orEmpty()
                },
                searchActive = state.searchActive,
                query = state.query,
                hasFilter = state.hasActiveFilter,
                offline = state.offline,
                showCrate = canSeeFavorites,
                captionFocus = captionFocus,
                onOpenCrate = onOpenPlaylists,
                onOpenFilter = { viewModel.setFilterOpen(true) },
                onQueryChange = viewModel::onQueryChange,
                onOpenSearch = { viewModel.setSearchActive(true) },
                onCloseSearch = { viewModel.setSearchActive(false) },
            )
        }
        if (state.filterOpen) {
            FilterPanel(
                platforms = state.platforms,
                selected = state.selectedPlatforms,
                onToggle = viewModel::togglePlatform,
                onClear = viewModel::clearFilters,
                onDismiss = { viewModel.setFilterOpen(false) },
            )
        }
    }
}

/** Bottom line: crate + filter (left), the current game title (or search field), search (right). */
@Composable
private fun CaptionBar(
    title: String,
    searchActive: Boolean,
    query: String,
    hasFilter: Boolean,
    offline: Boolean,
    showCrate: Boolean,
    captionFocus: FocusRequester,
    onOpenCrate: () -> Unit,
    onOpenFilter: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).cursorSection(captionFocus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (offline) {
            Icon(
                Icons.Filled.CloudOff,
                contentDescription = "Offline",
                tint = MutedText,
                modifier = Modifier.padding(start = 6.dp, end = 2.dp).size(20.dp),
            )
        }
        if (showCrate) {
            GlowIcon(Icons.Filled.LibraryMusic, "Crate", active = false, onClick = onOpenCrate)
        }
        GlowIcon(Icons.Filled.FilterList, "Filter", active = hasFilter, onClick = onOpenFilter)
        if (searchActive) {
            val searchFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { searchFocus.requestFocus() }
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search") },
                trailingIcon = {
                    FocusableIconButton(onClick = onCloseSearch) {
                        Icon(Icons.Filled.Close, contentDescription = "Close search")
                    }
                },
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color(0x33000000),
                    focusedContainerColor = Color(0x33000000),
                ),
                modifier = Modifier.weight(1f).focusRequester(searchFocus),
            )
        } else {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
        }
        GlowIcon(Icons.Filled.Search, "Search", active = searchActive, onClick = onOpenSearch)
    }
}

/** Icon button that glows brass (LED-style) when active. One dpad focus stop. */
@Composable
private fun GlowIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    FocusableIconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            if (active) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(BrassGlow.copy(alpha = 0.22f)))
            }
            Icon(icon, contentDescription = label, tint = if (active) Brass else Cream)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun FilterPanel(
    platforms: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { panelFocus.requestFocus() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = Felt,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } }
                .focusProperties { exit = { FocusRequester.Cancel } }
                .cursorSection(panelFocus),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Filter", style = MaterialTheme.typography.titleMedium, color = Cream)
                Spacer(Modifier.height(14.dp))
                Text("PLATFORM", style = MaterialTheme.typography.labelSmall, color = MutedText)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    platforms.forEach { platform ->
                        FocusHalo(onActivate = { onToggle(platform) }) {
                            FilterChip(
                                selected = platform in selected,
                                onClick = { onToggle(platform) },
                                label = { Text(platform) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Franchise and year filters coming soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    FocusHalo(onActivate = onClear) {
                        TextButton(onClick = onClear) { Text("Clear") }
                    }
                    Spacer(Modifier.weight(1f))
                    FocusHalo(onActivate = onDismiss) {
                        TextButton(onClick = onDismiss) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Shelf(
    games: List<GameWithMusic>,
    pagerState: PagerState,
    showFavorites: Boolean,
    focusRequester: FocusRequester,
    onGameClick: (GameWithMusic) -> Unit,
    onFavoritesClick: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cursor = LocalCursorState.current
    var shelfFocused by remember { mutableStateOf(false) }
    val pageOffset = if (showFavorites) 1 else 0

    fun stepTo(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, games.lastIndex + pageOffset)) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 20.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { shelfFocused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.ButtonL1, Key.DirectionLeft -> {
                        stepTo(pagerState.currentPage - 1)
                        true
                    }
                    Key.ButtonR1, Key.DirectionRight -> {
                        stepTo(pagerState.currentPage + 1)
                        true
                    }
                    Key.ButtonA, Key.DirectionCenter, Key.Enter -> {
                        val page = pagerState.currentPage
                        if (showFavorites && page == 0) {
                            onFavoritesClick()
                        } else {
                            games.getOrNull(page - pageOffset)?.let(onGameClick)
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 56.dp),
                pageSpacing = 20.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) { page ->
                val focused = shelfFocused && cursor.visible && page == pagerState.currentPage
                if (showFavorites && page == 0) {
                    FavoritesCase(focused = focused, onClick = onFavoritesClick)
                } else {
                    val game = games[page - pageOffset]
                    RecordCase(game, focused = focused, onClick = { onGameClick(game) })
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .shadow(12.dp)
                .background(ShelfPlank),
        )
    }
}

@Composable
private fun RecordCase(game: GameWithMusic, focused: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxHeight(0.9f)
                .aspectRatio(1f)
                .offset(x = 22.dp)
                .clip(CircleShape)
                .background(Color(0xFF111114)),
        ) {
            Box(
                Modifier
                    .fillMaxSize(0.09f)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A40)),
            )
        }
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .shadow(16.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(SleeveFallback)
                .pointerInput(game.romId) { detectTapGestures { onClick() } },
            contentAlignment = Alignment.Center,
        ) {
            CoverArt(game)
            SleeveCursorRing(focused)
        }
    }
}

/**
 * Cursor ring for the shelf's front sleeve: the brass hairline of the shared
 * ring style, drawn over the art (the felt fill would hide the cover, and a
 * plain border modifier would be painted under it).
 */
@Composable
private fun BoxScope.SleeveCursorRing(focused: Boolean) {
    if (focused) {
        Box(Modifier.matchParentSize().border(2.dp, Brass, RoundedCornerShape(4.dp)))
    }
}

/** The mixtape kept at the front of the crate: a drawn sleeve for liked tracks. */
@Composable
private fun FavoritesCase(focused: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxHeight(0.9f)
                .aspectRatio(1f)
                .offset(x = 22.dp)
                .clip(CircleShape)
                .background(Color(0xFF111114)),
        ) {
            Box(
                Modifier
                    .fillMaxSize(0.09f)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color(0xFF3A3A40)),
            )
        }
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .shadow(16.dp, RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(SleeveFallback)
                .pointerInput(Unit) { detectTapGestures { onClick() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = "Favorites",
                    tint = Brass,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "FAVORITES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                )
            }
            SleeveCursorRing(focused)
        }
    }
}

@Composable
private fun CoverArt(game: GameWithMusic) {
    if (game.coverUrl == null) {
        FallbackSleeveLabel(game.name)
        return
    }
    val painter = rememberAsyncImagePainter(model = game.coverUrl)
    Image(
        painter = painter,
        contentDescription = game.name,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    if (painter.state is AsyncImagePainter.State.Error) {
        FallbackSleeveLabel(game.name)
    }
}

@Composable
private fun FallbackSleeveLabel(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.titleSmall,
        color = Color(0xFFB8A98E),
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun CenterProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenterMessage(message: String, onRetry: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            FocusHalo(onActivate = onRetry) {
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}
