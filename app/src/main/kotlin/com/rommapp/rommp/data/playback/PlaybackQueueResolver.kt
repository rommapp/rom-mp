package com.rommapp.rommp.data.playback

import com.rommapp.rommp.data.download.DownloadDao
import com.rommapp.rommp.data.music.Track
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rebuilds playable [Track]s for persisted romFileIds from the local
 * cached_tracks/downloaded_tracks tables, so a queue can be restored without a
 * network round-trip. Ids that are no longer cached are dropped; input order is
 * kept; downloaded local audio takes priority over the stream URL.
 */
@Singleton
class PlaybackQueueResolver @Inject constructor(
    private val downloadDao: DownloadDao,
) {
    suspend fun resolve(romFileIds: List<Int>): List<Track> {
        if (romFileIds.isEmpty()) return emptyList()
        val cached = downloadDao.cachedTracksByFileIds(romFileIds).associateBy { it.romFileId }
        val downloaded = downloadDao.downloadedTracksByFileIds(romFileIds).associateBy { it.romFileId }
        return romFileIds.mapNotNull { id ->
            val entity = cached[id] ?: return@mapNotNull null
            Track(
                romFileId = entity.romFileId,
                romId = entity.gameRomId,
                title = entity.title,
                gameName = entity.gameName,
                platformName = entity.platformName,
                trackNumber = entity.trackNumber,
                durationSeconds = entity.durationSeconds,
                streamUrl = downloaded[id]?.audioUri ?: entity.streamUrl,
                coverUrl = entity.coverUrl,
                gameCoverUrl = entity.gameCoverUrl,
            )
        }
    }
}
