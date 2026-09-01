package com.rommapp.rommp.data.music

import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.download.CachedTrackEntity
import com.rommapp.rommp.data.download.DownloadDao
import com.rommapp.rommp.data.remote.RommApiFactory
import com.rommapp.rommp.data.remote.RommMusicApi
import com.rommapp.rommp.data.remote.dto.FacetValueDto
import com.rommapp.rommp.data.remote.dto.MusicTrackDto
import com.rommapp.rommp.data.remote.dto.PaginatedResponse
import com.rommapp.rommp.data.remote.fetchAllPages
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteMusicRepository @Inject constructor(
    private val apiFactory: RommApiFactory,
    private val tokenStore: TokenStore,
    private val downloadDao: DownloadDao,
) : MusicRepository {

    override suspend fun gamesWithMusic(
        search: String?,
        minDuration: Double,
    ): List<GameWithMusic> {
        val (baseUrl, token) = creds()
        val romsApi = apiFactory.createRomsApi(baseUrl, token)
        val roms = fetchAllPages(pageSize = 200) { limit, offset ->
            romsApi.getRoms(
                hasSoundtrack = true,
                searchTerm = search?.takeIf { it.isNotBlank() },
                limit = limit,
                offset = offset,
            )
        }
        val musicApi = apiFactory.createMusicApi(baseUrl, token)
        val qualifyingRomIds = fetchAllPages(pageSize = 500) { limit, offset ->
            musicApi.getTracks(minDuration = minDuration, limit = limit, offset = offset)
        }.mapTo(mutableSetOf()) { it.romId }
        return roms.filter { it.id in qualifyingRomIds }.map { rom ->
            GameWithMusic(
                romId = rom.id,
                name = rom.name.orEmpty(),
                platformName = rom.platformName ?: rom.platformSlug.orEmpty(),
                coverUrl = rom.pathCoverLarge?.let { encodedMediaUrl(baseUrl, it) },
            )
        }
    }

    override suspend fun gameTracks(romId: Int, minDuration: Double): List<Track> {
        val (baseUrl, token) = creds()
        val musicApi = apiFactory.createMusicApi(baseUrl, token)
        val items = fetchAllPages(pageSize = 500) { limit, offset ->
            musicApi.getTracks(
                romId = romId,
                minDuration = minDuration,
                limit = limit,
                offset = offset,
            )
        }
        // The game's box art is the outer/album cover (RomM has no game_cover_url
        // on tracks, so we resolve it from the ROM's path_cover_large).
        val gameCover = runCatching {
            apiFactory.createRomsApi(baseUrl, token).getRom(romId).pathCoverLarge
        }.getOrNull()?.let { encodedMediaUrl(baseUrl, it) }
        val tracks = items
            .filter { it.romId == romId }
            .sortedBy { it.track ?: Int.MAX_VALUE }
            .map { dto -> dto.toTrack(baseUrl).copy(gameCoverUrl = gameCover) }
        downloadDao.upsertCachedTracks(tracks.map { it.toCachedEntity(romId) })
        return tracks
    }

    override suspend fun cachedTracks(romId: Int): List<Track> =
        downloadDao.cachedTracks(romId).map { e ->
            Track(
                romFileId = e.romFileId,
                romId = e.gameRomId,
                title = e.title,
                gameName = e.gameName,
                platformName = e.platformName,
                trackNumber = e.trackNumber,
                durationSeconds = e.durationSeconds,
                streamUrl = e.streamUrl,
                coverUrl = e.coverUrl,
                gameCoverUrl = e.gameCoverUrl,
                isFavorite = e.isFavorite,
            )
        }

    private fun Track.toCachedEntity(romId: Int) = CachedTrackEntity(
        romFileId = romFileId,
        gameRomId = romId,
        title = title,
        gameName = gameName,
        platformName = platformName,
        trackNumber = trackNumber,
        durationSeconds = durationSeconds,
        streamUrl = streamUrl,
        coverUrl = coverUrl,
        gameCoverUrl = gameCoverUrl,
        isFavorite = isFavorite,
    )

    override suspend fun tracks(filters: MusicFilters): PaginatedResponse<MusicTrackDto> =
        api().getTracks(
            search = filters.search,
            artist = filters.artist,
            album = filters.album,
            genre = filters.genre,
            platformIds = filters.platformIds,
            year = filters.year,
            minDuration = filters.minDuration,
            maxDuration = filters.maxDuration,
            orderBy = filters.orderBy,
            orderDir = filters.orderDir,
            limit = filters.limit,
            offset = filters.offset,
        )

    override suspend fun artists(search: String?, platformIds: List<Int>?): PaginatedResponse<FacetValueDto> =
        api().getArtists(search = search, platformIds = platformIds)

    override suspend fun albums(search: String?, platformIds: List<Int>?): PaginatedResponse<FacetValueDto> =
        api().getAlbums(search = search, platformIds = platformIds)

    override suspend fun genres(search: String?, platformIds: List<Int>?): PaginatedResponse<FacetValueDto> =
        api().getGenres(search = search, platformIds = platformIds)

    private suspend fun api(): RommMusicApi {
        val (baseUrl, token) = creds()
        return apiFactory.createMusicApi(baseUrl, token)
    }

    private suspend fun creds(): Pair<String, String> {
        val baseUrl = tokenStore.baseUrl.first() ?: error("No server configured")
        val token = tokenStore.token.first() ?: error("Not authenticated")
        return baseUrl to token
    }
}
