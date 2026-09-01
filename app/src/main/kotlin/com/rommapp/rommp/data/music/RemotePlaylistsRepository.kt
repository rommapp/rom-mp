package com.rommapp.rommp.data.music

import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.download.DownloadDao
import com.rommapp.rommp.data.remote.RommApiFactory
import com.rommapp.rommp.data.remote.RommPlaylistsApi
import com.rommapp.rommp.data.remote.dto.PlaylistCreateRequest
import com.rommapp.rommp.data.remote.dto.PlaylistDto
import com.rommapp.rommp.data.remote.dto.PlaylistTrackIdsRequest
import com.rommapp.rommp.data.remote.dto.PlaylistUpdateRequest
import com.rommapp.rommp.data.remote.fetchAllPages
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemotePlaylistsRepository @Inject constructor(
    private val apiFactory: RommApiFactory,
    private val tokenStore: TokenStore,
    private val downloadDao: DownloadDao,
    private val staleTrackEvents: StaleTrackEvents,
) : PlaylistsRepository {

    /** The authed user's id; stable for the life of the token, so fetched once. */
    @Volatile
    private var cachedUserId: Int? = null

    override suspend fun playlists(): List<Playlist> {
        val (baseUrl, token) = creds()
        val userId = currentUserId(baseUrl, token)
        return apiFactory.createPlaylistsApi(baseUrl, token)
            .getPlaylists()
            .map { it.toPlaylist(userId) }
    }

    override suspend fun playlist(id: Int): Playlist {
        val (baseUrl, token) = creds()
        val userId = currentUserId(baseUrl, token)
        return apiFactory.createPlaylistsApi(baseUrl, token)
            .getPlaylist(id)
            .toPlaylist(userId)
    }

    override suspend fun createPlaylist(name: String, description: String?, isPublic: Boolean): Playlist {
        val (baseUrl, token) = creds()
        val userId = currentUserId(baseUrl, token)
        return apiFactory.createPlaylistsApi(baseUrl, token)
            .createPlaylist(PlaylistCreateRequest(name = name, description = description, isPublic = isPublic))
            .toPlaylist(userId)
    }

    override suspend fun updatePlaylist(
        id: Int,
        name: String?,
        description: String?,
        isPublic: Boolean?,
    ): Playlist {
        val (baseUrl, token) = creds()
        val userId = currentUserId(baseUrl, token)
        return apiFactory.createPlaylistsApi(baseUrl, token)
            .updatePlaylist(id, PlaylistUpdateRequest(name = name, description = description, isPublic = isPublic))
            .toPlaylist(userId)
    }

    override suspend fun deletePlaylist(id: Int) {
        api().deletePlaylist(id)
    }

    override suspend fun playlistTracks(id: Int, limit: Int, offset: Int): List<Track> {
        val (baseUrl, token) = creds()
        val api = apiFactory.createPlaylistsApi(baseUrl, token)
        return fetchAllPages(pageSize = limit, startOffset = offset) { pageLimit, pageOffset ->
            api.getPlaylistTracks(id, limit = pageLimit, offset = pageOffset)
        }.toTracksWithGameCovers(baseUrl, apiFactory.createRomsApi(baseUrl, token), downloadDao)
    }

    override suspend fun playlistTrackRefs(id: Int, limit: Int): List<TrackRef> {
        val (baseUrl, token) = creds()
        val api = apiFactory.createPlaylistsApi(baseUrl, token)
        return fetchAllPages(pageSize = limit) { pageLimit, pageOffset ->
            api.getPlaylistTracks(id, limit = pageLimit, offset = pageOffset)
        }.map { TrackRef(romFileId = it.romFileId) }
    }

    override suspend fun addTracks(id: Int, romFileIds: List<Int>): Int =
        runCatching { api().addTracks(id, PlaylistTrackIdsRequest(romFileIds)).added }
            .getOrElse { throw it.mapStaleTrackError(staleTrackEvents) }

    override suspend fun removeTracks(id: Int, romFileIds: List<Int>): Int =
        runCatching { api().removeTracks(id, PlaylistTrackIdsRequest(romFileIds)).removed }
            .getOrElse { throw it.mapStaleTrackError(staleTrackEvents) }

    override suspend fun reorderTracks(id: Int, romFileIds: List<Int>) {
        api().setTrackOrder(id, PlaylistTrackIdsRequest(romFileIds))
    }

    private fun PlaylistDto.toPlaylist(currentUserId: Int) = Playlist(
        id = id,
        name = name,
        description = description,
        isPublic = isPublic,
        ownerUsername = ownerUsername,
        trackCount = trackCount,
        isOwned = userId == currentUserId,
    )

    private suspend fun currentUserId(baseUrl: String, token: String): Int =
        cachedUserId ?: apiFactory.createUsersApi(baseUrl, token).getCurrentUser().id
            .also { cachedUserId = it }

    private suspend fun api(): RommPlaylistsApi {
        val (baseUrl, token) = creds()
        return apiFactory.createPlaylistsApi(baseUrl, token)
    }

    private suspend fun creds(): Pair<String, String> {
        val baseUrl = tokenStore.baseUrl.first() ?: error("No server configured")
        val token = tokenStore.token.first() ?: error("Not authenticated")
        return baseUrl to token
    }
}
