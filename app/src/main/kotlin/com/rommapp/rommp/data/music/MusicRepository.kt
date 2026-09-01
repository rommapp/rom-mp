package com.rommapp.rommp.data.music

import com.rommapp.rommp.data.remote.dto.FacetValueDto
import com.rommapp.rommp.data.remote.dto.MusicTrackDto
import com.rommapp.rommp.data.remote.dto.PaginatedResponse

/** Tracks shorter than this are jingles/stingers, hidden from album views. */
const val DEFAULT_MIN_TRACK_SECONDS = 30.0

/**
 * Reads the soundtrack library via RomM's music API. Implemented by
 * [RemoteMusicRepository]; an interface so a fake can back UI development.
 */
interface MusicRepository {
    /**
     * Games that have soundtracks, for the library shelf. Games with no track
     * meeting [minDuration] are omitted (their album view would be empty).
     */
    suspend fun gamesWithMusic(
        search: String? = null,
        minDuration: Double = DEFAULT_MIN_TRACK_SECONDS,
    ): List<GameWithMusic>

    /** Music tracks for one game (album), longer than [minDuration]s, in track order. */
    suspend fun gameTracks(
        romId: Int,
        minDuration: Double = DEFAULT_MIN_TRACK_SECONDS,
    ): List<Track>

    /** The full known track list for a game from the local cache (offline). */
    suspend fun cachedTracks(romId: Int): List<Track>
    suspend fun tracks(filters: MusicFilters = MusicFilters()): PaginatedResponse<MusicTrackDto>
    suspend fun artists(search: String? = null, platformIds: List<Int>? = null): PaginatedResponse<FacetValueDto>
    suspend fun albums(search: String? = null, platformIds: List<Int>? = null): PaginatedResponse<FacetValueDto>
    suspend fun genres(search: String? = null, platformIds: List<Int>? = null): PaginatedResponse<FacetValueDto>
}
