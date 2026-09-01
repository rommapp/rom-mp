package com.rommapp.rommp.data.music

import com.rommapp.rommp.data.download.DownloadDao
import com.rommapp.rommp.data.remote.RommRomsApi
import com.rommapp.rommp.data.remote.dto.MusicTrackDto

/** Server track row -> playable [Track] with absolute URLs. */
internal fun MusicTrackDto.toTrack(baseUrl: String): Track =
    Track(
        romFileId = romFileId,
        romId = romId,
        title = title.orEmpty().ifBlank { streamUrl.substringAfterLast('/') },
        gameName = gameName.orEmpty(),
        platformName = platformName.ifBlank { platformSlug },
        trackNumber = track,
        durationSeconds = durationSeconds,
        streamUrl = baseUrl + streamUrl,
        coverUrl = coverUrl?.let { encodedMediaUrl(baseUrl, it) },
        isFavorite = isFavorite,
    )

/**
 * Maps a cross-game track list (favorites, playlists), resolving each game's
 * box art once per distinct game: from cached_tracks where already known, else
 * one roms lookup. Tracks whose game has no cover keep gameCoverUrl null.
 */
internal suspend fun List<MusicTrackDto>.toTracksWithGameCovers(
    baseUrl: String,
    romsApi: RommRomsApi,
    downloadDao: DownloadDao,
): List<Track> {
    if (isEmpty()) return emptyList()
    val romIds = map { it.romId }.distinct()
    val cached = downloadDao.cachedGameCovers(romIds).associate { it.gameRomId to it.gameCoverUrl }
    val covers = romIds.associateWith { romId ->
        cached[romId]
            ?: runCatching { romsApi.getRom(romId).pathCoverLarge }
                .getOrNull()
                ?.let { encodedMediaUrl(baseUrl, it) }
    }
    return map { it.toTrack(baseUrl).copy(gameCoverUrl = covers[it.romId]) }
}
