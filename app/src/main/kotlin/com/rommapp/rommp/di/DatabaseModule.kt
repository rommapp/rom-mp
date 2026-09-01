package com.rommapp.rommp.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rommapp.rommp.data.download.AppDatabase
import com.rommapp.rommp.data.download.DownloadDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_tracks (
                romFileId INTEGER NOT NULL PRIMARY KEY,
                gameRomId INTEGER NOT NULL,
                title TEXT NOT NULL,
                gameName TEXT NOT NULL,
                platformName TEXT NOT NULL,
                trackNumber INTEGER,
                durationSeconds REAL,
                streamUrl TEXT NOT NULL,
                coverUrl TEXT
            )
            """.trimIndent(),
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cached_tracks ADD COLUMN gameCoverUrl TEXT")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN md5Hash TEXT")
        db.execSQL("ALTER TABLE cached_tracks ADD COLUMN md5Hash TEXT")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cached_tracks ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}

// Drops the now-unused md5Hash column. rom_file_id is stable across rescans, so
// the id-churn reconciliation md5 backed is gone. SQLite before 3.35 has no DROP
// COLUMN, so recreate each table without the column (minSdk 26).
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cached_tracks_new (
                romFileId INTEGER NOT NULL PRIMARY KEY,
                gameRomId INTEGER NOT NULL,
                title TEXT NOT NULL,
                gameName TEXT NOT NULL,
                platformName TEXT NOT NULL,
                trackNumber INTEGER,
                durationSeconds REAL,
                streamUrl TEXT NOT NULL,
                coverUrl TEXT,
                gameCoverUrl TEXT,
                isFavorite INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO cached_tracks_new (
                romFileId, gameRomId, title, gameName, platformName,
                trackNumber, durationSeconds, streamUrl, coverUrl, gameCoverUrl, isFavorite
            )
            SELECT romFileId, gameRomId, title, gameName, platformName,
                trackNumber, durationSeconds, streamUrl, coverUrl, gameCoverUrl, isFavorite
            FROM cached_tracks
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE cached_tracks")
        db.execSQL("ALTER TABLE cached_tracks_new RENAME TO cached_tracks")

        db.execSQL(
            """
            CREATE TABLE downloaded_tracks_new (
                romFileId INTEGER NOT NULL PRIMARY KEY,
                gameRomId INTEGER NOT NULL,
                title TEXT NOT NULL,
                gameName TEXT NOT NULL,
                trackNumber INTEGER,
                durationSeconds REAL,
                audioUri TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO downloaded_tracks_new (
                romFileId, gameRomId, title, gameName, trackNumber, durationSeconds, audioUri
            )
            SELECT romFileId, gameRomId, title, gameName, trackNumber, durationSeconds, audioUri
            FROM downloaded_tracks
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE downloaded_tracks")
        db.execSQL("ALTER TABLE downloaded_tracks_new RENAME TO downloaded_tracks")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "rommp.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao = database.downloadDao()
}
