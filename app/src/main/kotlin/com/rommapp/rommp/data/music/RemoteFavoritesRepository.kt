package com.rommapp.rommp.data.music

import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.download.DownloadDao
import com.rommapp.rommp.data.remote.RommApiFactory
import com.rommapp.rommp.data.remote.dto.FavoriteIdsRequest
import com.rommapp.rommp.data.remote.fetchAllPages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteFavoritesRepository @Inject constructor(
    private val apiFactory: RommApiFactory,
    private val tokenStore: TokenStore,
    private val downloadDao: DownloadDao,
    private val staleTrackEvents: StaleTrackEvents,
) : FavoritesRepository {

    private val _overrides = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    override val overrides: StateFlow<Map<Int, Boolean>> = _overrides.asStateFlow()

    override suspend fun favorites(limit: Int, offset: Int): List<Track> {
        val (baseUrl, token) = creds()
        val api = apiFactory.createFavoritesApi(baseUrl, token)
        return fetchAllPages(pageSize = limit, startOffset = offset) { pageLimit, pageOffset ->
            api.getFavorites(limit = pageLimit, offset = pageOffset)
        }.toTracksWithGameCovers(baseUrl, apiFactory.createRomsApi(baseUrl, token), downloadDao)
    }

    override suspend fun setFavorite(romFileId: Int, favorite: Boolean) {
        val previous = _overrides.value[romFileId]
        _overrides.update { it + (romFileId to favorite) }
        runCatching {
            val (baseUrl, token) = creds()
            val api = apiFactory.createFavoritesApi(baseUrl, token)
            val body = FavoriteIdsRequest(listOf(romFileId))
            if (favorite) api.addFavorites(body) else api.removeFavorites(body)
        }.onFailure { error ->
            _overrides.update { current ->
                when {
                    current[romFileId] != favorite -> current
                    previous == null -> current - romFileId
                    else -> current + (romFileId to previous)
                }
            }
            throw error.mapStaleTrackError(staleTrackEvents)
        }
    }

    private suspend fun creds(): Pair<String, String> {
        val baseUrl = tokenStore.baseUrl.first() ?: error("No server configured")
        val token = tokenStore.token.first() ?: error("Not authenticated")
        return baseUrl to token
    }
}
