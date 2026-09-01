package com.rommapp.rommp.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.music.GameWithMusic
import com.rommapp.rommp.data.music.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Progress of the active album download; [failed] counts tracks that errored. */
data class DownloadProgress(val romId: Int, val done: Int, val total: Int, val failed: Int = 0)

/**
 * Downloads album soundtracks for offline use: audio goes to the public Music
 * collection (MediaStore), while game/track metadata and the cover art are kept
 * in app storage so the library works offline. App-scoped so downloads survive
 * screen navigation.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val tokenStore: TokenStore,
    private val dao: DownloadDao,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val downloadedIds: Flow<Set<Int>> = dao.observeGameIds().map { it.toSet() }

    val downloadedTrackIds: Flow<Set<Int>> = dao.observeDownloadedTrackIds().map { it.toSet() }

    val downloadedGames: Flow<List<GameWithMusic>> = dao.observeGames().map { games ->
        games.map { g -> GameWithMusic(g.romId, g.name, g.platformName, g.coverPath?.let { "file://$it" }) }
    }

    private val _active = MutableStateFlow<DownloadProgress?>(null)
    val active: StateFlow<DownloadProgress?> = _active.asStateFlow()

    private val _downloading = MutableStateFlow<Set<Int>>(emptySet())
    val downloadingTrackIds: StateFlow<Set<Int>> = _downloading.asStateFlow()

    fun downloadAlbum(game: GameWithMusic, tracks: List<Track>) {
        if (tracks.isEmpty() || _active.value != null) return
        scope.launch {
            _active.value = DownloadProgress(game.romId, 0, tracks.size)
            var failed = 0
            runCatching { ensureGame(game) }
                .onSuccess {
                    val already = dao.observeDownloadedTrackIds().first().toSet()
                    tracks.forEachIndexed { index, track ->
                        if (track.romFileId !in already) {
                            _downloading.update { it + track.romFileId }
                            runCatching { writeAndRecordTrack(game, track) }.onFailure { failed++ }
                            _downloading.update { it - track.romFileId }
                        }
                        _active.value = DownloadProgress(game.romId, index + 1, tracks.size, failed)
                    }
                }
                .onFailure {
                    _active.value = DownloadProgress(game.romId, tracks.size, tracks.size, tracks.size)
                }
            _active.value = null
        }
    }

    fun downloadTrack(game: GameWithMusic, track: Track) {
        if (track.romFileId in _downloading.value) return
        scope.launch {
            _downloading.update { it + track.romFileId }
            runCatching {
                ensureGame(game)
                writeAndRecordTrack(game, track)
            }
            _downloading.update { it - track.romFileId }
        }
    }

    private suspend fun ensureGame(game: GameWithMusic) {
        if (dao.getGame(game.romId) == null) {
            val coverPath = game.coverUrl?.let { runCatching { downloadCover(it, game.romId) }.getOrNull() }
            dao.upsertGame(
                DownloadedGameEntity(game.romId, game.name, game.platformName, coverPath, System.currentTimeMillis()),
            )
        }
    }

    private suspend fun writeAndRecordTrack(game: GameWithMusic, track: Track) {
        val uri = writeTrackToMusic(track, game.name)
        dao.upsertTracks(
            listOf(
                DownloadedTrackEntity(
                    romFileId = track.romFileId,
                    gameRomId = game.romId,
                    title = track.title,
                    gameName = track.gameName,
                    trackNumber = track.trackNumber,
                    durationSeconds = track.durationSeconds,
                    audioUri = uri.toString(),
                ),
            ),
        )
    }

    suspend fun isDownloaded(romId: Int): Boolean = dao.isGameDownloaded(romId) > 0

    /** Downloaded tracks as playable [Track]s (audio = local content URI, cover = local file). */
    suspend fun downloadedTracks(romId: Int): List<Track> {
        val game = dao.getGame(romId)
        val coverUrl = game?.coverPath?.let { "file://$it" }
        val platformName = game?.platformName.orEmpty()
        return dao.tracksForGame(romId).map { e ->
            Track(
                romFileId = e.romFileId,
                romId = e.gameRomId,
                title = e.title,
                gameName = e.gameName,
                platformName = platformName,
                trackNumber = e.trackNumber,
                durationSeconds = e.durationSeconds,
                streamUrl = e.audioUri,
                coverUrl = null,
                gameCoverUrl = coverUrl,
            )
        }
    }

    fun deleteAlbum(romId: Int) {
        scope.launch {
            dao.tracksForGame(romId).forEach { track ->
                runCatching { context.contentResolver.delete(Uri.parse(track.audioUri), null, null) }
            }
            dao.getGame(romId)?.coverPath?.let { runCatching { File(it).delete() } }
            dao.deleteTracks(romId)
            dao.deleteGame(romId)
        }
    }

    private suspend fun writeTrackToMusic(track: Track, gameName: String): Uri = withContext(Dispatchers.IO) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Offline download requires Android 10+" }
        val token = tokenStore.token.first().orEmpty()
        val request = Request.Builder()
            .url(track.streamUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} for ${track.title}" }
            val body = response.body ?: error("Empty body for ${track.title}")
            val resolver = context.contentResolver
            val extension = sourceExtension(track.streamUrl)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, downloadFileName(track.trackNumber, track.title, extension))
                put(MediaStore.Audio.Media.MIME_TYPE, audioMimeType(extension))
                put(MediaStore.Audio.Media.RELATIVE_PATH, downloadRelativePath(track.platformName, gameName))
                put(MediaStore.Audio.Media.TITLE, track.title)
                put(MediaStore.Audio.Media.ALBUM, gameName)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
            resolver.openOutputStream(uri)?.use { out -> body.byteStream().copyTo(out) }
                ?: error("No output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            uri
        }
    }

    private suspend fun downloadCover(url: String, romId: Int): String = withContext(Dispatchers.IO) {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "Cover HTTP ${response.code}" }
            val dir = File(context.filesDir, "covers").apply { mkdirs() }
            val file = File(dir, "$romId.jpg")
            response.body?.byteStream()?.use { input -> file.outputStream().use { input.copyTo(it) } }
            file.absolutePath
        }
    }

}

/** MediaStore folder for a track: Music/RomM/<platform>/<game>, platform omitted when unknown. */
internal fun downloadRelativePath(platformName: String, gameName: String): String {
    val platform = platformName.takeIf { it.isNotBlank() }?.let { "${sanitizeName(it)}/" }.orEmpty()
    return "Music/RomM/$platform${sanitizeName(gameName)}"
}

/** MediaStore display name, track-numbered when the server knows the position. */
internal fun downloadFileName(trackNumber: Int?, title: String, extension: String): String {
    val number = trackNumber?.let { "%02d - ".format(it) } ?: ""
    return "$number${sanitizeName(title)}.$extension"
}

/**
 * File extension of the source file, taken from the stream URL's file name.
 * Allowlisted to a plain alphanumeric token because it lands in the MediaStore
 * display name unescaped, unlike the sanitized path segments.
 */
internal fun sourceExtension(streamUrl: String): String =
    Uri.decode(streamUrl.substringAfterLast('/').substringBefore('?'))
        .substringAfterLast('.', "")
        .lowercase()
        .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "mp3"

internal fun audioMimeType(extension: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "audio/mpeg"

/**
 * Folds a server-supplied name into one safe path segment: separators and the
 * characters Android refuses become underscores, and surrounding dots and spaces
 * go, so a name can never add a path segment or climb out of the album folder.
 * Never yields a blank name.
 */
internal fun sanitizeName(name: String): String =
    name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        .trim { it.isWhitespace() || it == '.' }
        .ifBlank { "track" }
