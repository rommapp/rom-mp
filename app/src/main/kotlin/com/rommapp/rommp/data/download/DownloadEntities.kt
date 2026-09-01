package com.rommapp.rommp.data.download

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A game whose soundtrack has been downloaded for offline use. */
@Entity(tableName = "downloaded_games")
data class DownloadedGameEntity(
    @PrimaryKey val romId: Int,
    val name: String,
    val platformName: String,
    /** App-private file path of the cover art (for the offline vinyl). */
    val coverPath: String?,
    val downloadedAt: Long,
)

/**
 * Cached track metadata for every track we've seen (not the audio). Lets the
 * album screen show the full known track list offline, greying out ones that
 * aren't downloaded.
 */
@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val romFileId: Int,
    val gameRomId: Int,
    val title: String,
    val gameName: String,
    val platformName: String,
    val trackNumber: Int?,
    val durationSeconds: Double?,
    val streamUrl: String,
    val coverUrl: String?,
    val gameCoverUrl: String? = null,
    /** Last server-known favorite state, so hearts render correctly offline. */
    val isFavorite: Boolean = false,
)

/** A downloaded track. The audio itself lives in public Music (MediaStore). */
@Entity(tableName = "downloaded_tracks")
data class DownloadedTrackEntity(
    @PrimaryKey val romFileId: Int,
    val gameRomId: Int,
    val title: String,
    val gameName: String,
    val trackNumber: Int?,
    val durationSeconds: Double?,
    /** content:// URI of the mp3 in the public Music collection. */
    val audioUri: String,
)
