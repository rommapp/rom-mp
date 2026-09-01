package com.rommapp.rommp.data.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.music.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Owns a single ExoPlayer for streaming soundtrack tracks and acts as the
 * app-facing playback facade. The stream endpoint is auth-scoped, so the Bearer
 * token is resolved from [TokenStore] per HTTP request. [PlaybackService] wraps
 * the same player in a MediaSession for background playback; this class starts
 * that service whenever playback begins. All player access is marshalled onto
 * the main thread.
 */
@Singleton
@OptIn(UnstableApi::class)
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenStore: TokenStore,
    private val stateStore: PlaybackStateStore,
    private val queueResolver: PlaybackQueueResolver,
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private val dataSourceFactory = ResolvingDataSource.Factory(
        DefaultDataSource.Factory(context, DefaultHttpDataSource.Factory()),
    ) { dataSpec ->
        val headers = streamAuthHeaders(dataSpec.uri) {
            runBlocking { tokenStore.baseUrl.first() to tokenStore.token.first() }
        }
        if (headers.isEmpty()) dataSpec else dataSpec.withAdditionalHeaders(headers)
    }

    private var player: ExoPlayer? = null
    private var queue: List<Track> = emptyList()
    private var positionJob: Job? = null
    private var lastError: String? = null
    private var restoreSettled = false

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    init {
        scope.launch {
            _state.subscriptionCount.first { it > 0 }
            restorePersistedQueue()
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) lastError = null
            publish()
            if (isPlaying) {
                startPolling()
            } else {
                stopPolling()
                persistQueue()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastError = null
            publish()
            persistQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) = publish()

        override fun onPlayerError(error: PlaybackException) {
            lastError = error.message ?: error.errorCodeName
            publish()
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        scope.launch {
            restoreSettled = true
            queue = tracks
            lastError = null
            val exo = ensurePlayer()
            val start = startIndex.coerceIn(0, tracks.lastIndex)
            exo.setMediaItems(tracks.map { it.toMediaItem() }, start, 0L)
            if (exo.shuffleModeEnabled) exo.setShuffleOrder(headFirstShuffleOrder(tracks.size, start))
            exo.prepare()
            exo.play()
            startService()
            publish()
            stateStore.save(tracks.map { it.romFileId }, start, 0L)
        }
    }

    fun togglePlayPause() = onPlayer {
        if (it.isPlaying) {
            it.pause()
        } else {
            if (it.playbackState == Player.STATE_IDLE) it.prepare()
            it.play()
            startService()
        }
    }

    fun next() = onPlayer { it.seekToNextMediaItem() }
    fun previous() = onPlayer { it.seekToPreviousMediaItem() }

    fun playIndex(index: Int) = onPlayer {
        it.seekTo(index, 0L)
        if (it.playbackState == Player.STATE_IDLE) it.prepare()
        it.play()
        startService()
    }

    fun seekTo(positionMs: Long) = onPlayer { it.seekTo(positionMs); publish() }

    fun setShuffle(enabled: Boolean) = onPlayer { p ->
        if (enabled && p.mediaItemCount > 0) {
            p.setShuffleOrder(headFirstShuffleOrder(p.mediaItemCount, p.currentMediaItemIndex))
        }
        p.shuffleModeEnabled = enabled
        publish()
    }

    /**
     * Builds a shuffle order whose explicit index array starts at [headIndex] followed
     * by all other item indices in random order. DefaultShuffleOrder treats the array
     * as the literal playback sequence (element 0 is the first index of the cycle).
     */
    private fun headFirstShuffleOrder(itemCount: Int, headIndex: Int): DefaultShuffleOrder {
        val head = headIndex.coerceIn(0, itemCount - 1)
        val rest = (0 until itemCount).filter { it != head }.shuffled()
        val order = IntArray(itemCount).also { arr ->
            arr[0] = head
            rest.forEachIndexed { i, index -> arr[i + 1] = index }
        }
        return DefaultShuffleOrder(order, Random.nextLong())
    }

    /** Cycles OFF -> repeat ALL -> repeat ONE -> OFF. */
    fun cycleRepeat() = onPlayer { p ->
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publish()
    }

    /**
     * The shared player for [PlaybackService]'s MediaSession, built on first use.
     * Main thread only.
     */
    internal fun acquirePlayer(): ExoPlayer = ensurePlayer()

    /**
     * Rebuilds the last session's queue from the local track cache, cued at the
     * saved track and position but paused. Runs once, on the first state
     * subscription, and yields to any queue the user starts in the meantime.
     */
    private suspend fun restorePersistedQueue() {
        if (restoreSettled) return
        restoreSettled = true
        val saved = stateStore.load() ?: return
        val tracks = runCatching { queueResolver.resolve(saved.romFileIds) }.getOrDefault(emptyList())
        if (tracks.isEmpty() || queue.isNotEmpty()) return
        queue = tracks
        val currentId = saved.romFileIds.getOrNull(saved.currentIndex)
        val index = tracks.indexOfFirst { it.romFileId == currentId }.coerceAtLeast(0)
        val position = if (tracks.getOrNull(index)?.romFileId == currentId) saved.positionMs else 0L
        ensurePlayer().setMediaItems(tracks.map { it.toMediaItem() }, index, position)
        publish()
    }

    private fun persistQueue() {
        val p = player ?: return
        if (queue.isEmpty()) return
        val ids = queue.map { it.romFileId }
        val index = p.currentMediaItemIndex
        val position = p.currentPosition.coerceAtLeast(0)
        scope.launch { stateStore.save(ids, index, position) }
    }

    private fun startService() {
        runCatching { context.startService(Intent(context, PlaybackService::class.java)) }
    }

    private fun ensurePlayer(): ExoPlayer =
        player ?: ExoPlayer.Builder(context)
            // DefaultDataSource handles content:// / file:// (downloaded tracks) and
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also {
                it.addListener(listener)
                player = it
            }

    private fun onPlayer(block: (ExoPlayer) -> Unit) {
        scope.launch { player?.let(block) }
    }

    private fun publish() {
        val p = player
        _state.value = PlaybackState(
            queue = queue,
            currentIndex = p?.currentMediaItemIndex ?: -1,
            isPlaying = p?.isPlaying == true,
            positionMs = p?.currentPosition?.coerceAtLeast(0) ?: 0,
            durationMs = p?.duration?.takeIf { it > 0 } ?: 0,
            shuffle = p?.shuffleModeEnabled == true,
            repeatMode = when (p?.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            error = lastError,
        )
    }

    /** Position refresh cadence while playing; fast enough that the scrubber tracks smoothly. */
    private companion object {
        const val POSITION_POLL_MS = 500L
    }

    private fun startPolling() {
        stopPolling()
        positionJob = scope.launch {
            _state.subscriptionCount.collectLatest { subscribers ->
                if (subscribers > 0) {
                    while (true) {
                        publish()
                        delay(POSITION_POLL_MS)
                    }
                }
            }
        }
    }

    private fun stopPolling() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun Track.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(romFileId.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(gameName)
                    .apply { coverUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .build(),
            )
            .build()
}

/**
 * Request headers for a media URI: the Bearer header when [uri] names the paired
 * server's origin, otherwise none.
 *
 * The MediaSession accepts outside controllers that can hand the player arbitrary
 * URIs, and a redirect can leave the server, so scheme, host and port must all
 * match the paired origin before the token travels. Local URIs (content://,
 * file://) never carry credentials, so [credentials] is not consulted for them.
 */
internal fun streamAuthHeaders(
    uri: Uri,
    credentials: () -> Pair<String?, String?>,
): Map<String, String> {
    val scheme = uri.scheme
    if (scheme != "http" && scheme != "https") return emptyMap()
    val (baseUrl, token) = credentials()
    if (token.isNullOrBlank()) return emptyMap()
    val base = baseUrl?.let(Uri::parse) ?: return emptyMap()
    val pairedOrigin = base.scheme == scheme &&
        base.host == uri.host &&
        base.port == uri.port
    return if (pairedOrigin) mapOf("Authorization" to "Bearer $token") else emptyMap()
}
