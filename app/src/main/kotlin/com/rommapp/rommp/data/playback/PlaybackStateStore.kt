package com.rommapp.rommp.data.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.playbackDataStore by preferencesDataStore(name = "playback")

/** The last session's queue, for paused restore on relaunch. */
data class PersistedQueue(
    val romFileIds: List<Int>,
    val currentIndex: Int,
    val positionMs: Long,
)

/** Persists the playing queue (track ids + listening spot) across process death. */
@Singleton
class PlaybackStateStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.playbackDataStore

    suspend fun save(romFileIds: List<Int>, currentIndex: Int, positionMs: Long) {
        store.edit {
            it[KEY_QUEUE] = romFileIds.joinToString(",")
            it[KEY_INDEX] = currentIndex
            it[KEY_POSITION] = positionMs
        }
    }

    suspend fun load(): PersistedQueue? {
        val prefs = store.data.first()
        val ids = prefs[KEY_QUEUE]?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        if (ids.isEmpty()) return null
        return PersistedQueue(
            romFileIds = ids,
            currentIndex = prefs[KEY_INDEX] ?: 0,
            positionMs = prefs[KEY_POSITION] ?: 0L,
        )
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val KEY_QUEUE = stringPreferencesKey("queue_rom_file_ids")
        val KEY_INDEX = intPreferencesKey("current_index")
        val KEY_POSITION = longPreferencesKey("position_ms")
    }
}
