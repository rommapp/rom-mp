package com.rommapp.rommp.data.download

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Projection of a game's cached box-art URL, keyed by the game's rom id. */
data class GameCoverRow(
    val gameRomId: Int,
    val gameCoverUrl: String,
)

@Dao
interface DownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGame(game: DownloadedGameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<DownloadedTrackEntity>)

    @Query("SELECT * FROM downloaded_games ORDER BY name")
    fun observeGames(): Flow<List<DownloadedGameEntity>>

    @Query("SELECT romId FROM downloaded_games")
    fun observeGameIds(): Flow<List<Int>>

    @Query("SELECT romFileId FROM downloaded_tracks")
    fun observeDownloadedTrackIds(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedTracks(tracks: List<CachedTrackEntity>)

    @Query("SELECT * FROM cached_tracks WHERE gameRomId = :romId ORDER BY trackNumber")
    suspend fun cachedTracks(romId: Int): List<CachedTrackEntity>

    @Query("SELECT * FROM cached_tracks WHERE romFileId IN (:romFileIds)")
    suspend fun cachedTracksByFileIds(romFileIds: List<Int>): List<CachedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE romFileId IN (:romFileIds)")
    suspend fun downloadedTracksByFileIds(romFileIds: List<Int>): List<DownloadedTrackEntity>

    @Query(
        "SELECT DISTINCT gameRomId, gameCoverUrl FROM cached_tracks " +
            "WHERE gameRomId IN (:romIds) AND gameCoverUrl IS NOT NULL",
    )
    suspend fun cachedGameCovers(romIds: List<Int>): List<GameCoverRow>

    @Query("SELECT * FROM downloaded_games WHERE romId = :romId")
    suspend fun getGame(romId: Int): DownloadedGameEntity?

    @Query("SELECT * FROM downloaded_tracks WHERE gameRomId = :romId ORDER BY trackNumber")
    suspend fun tracksForGame(romId: Int): List<DownloadedTrackEntity>

    @Query("SELECT * FROM downloaded_tracks WHERE gameRomId = :romId ORDER BY trackNumber")
    fun observeTracksForGame(romId: Int): Flow<List<DownloadedTrackEntity>>

    @Query("SELECT COUNT(*) FROM downloaded_games WHERE romId = :romId")
    suspend fun isGameDownloaded(romId: Int): Int

    @Query("DELETE FROM downloaded_games WHERE romId = :romId")
    suspend fun deleteGame(romId: Int)

    @Query("DELETE FROM downloaded_tracks WHERE gameRomId = :romId")
    suspend fun deleteTracks(romId: Int)
}
