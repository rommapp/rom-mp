package com.rommapp.rommp.data.download

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadedGameEntity::class, DownloadedTrackEntity::class, CachedTrackEntity::class],
    version = 6,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
