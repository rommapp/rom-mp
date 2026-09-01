package com.rommapp.rommp.data.music

import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.download.DownloadDao
import com.rommapp.rommp.data.remote.RommApiFactory
import com.rommapp.rommp.data.remote.RommFavoritesApi
import com.rommapp.rommp.data.remote.dto.FavoriteIdsRequest
import com.rommapp.rommp.data.remote.dto.FavoritesAddedResponse
import com.rommapp.rommp.data.remote.dto.FavoritesRemovedResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Pins the optimistic heart. The override is what every screen renders, so it
 * has to appear before the server call and be rolled back to exactly its former
 * value (absent, or the opposite state) when the call fails.
 */
class RemoteFavoritesRepositoryTest {

    private val favoritesApi = mockk<RommFavoritesApi>()
    private val apiFactory = mockk<RommApiFactory>()
    private val tokenStore = mockk<TokenStore>()
    private val downloadDao = mockk<DownloadDao>(relaxed = true)
    private val staleTrackEvents = mockk<StaleTrackEvents>(relaxed = true)

    private val repository = RemoteFavoritesRepository(apiFactory, tokenStore, downloadDao, staleTrackEvents)

    private fun pairedServer(baseUrl: String? = "https://romm.example.com", token: String? = "rmm_secret") {
        every { tokenStore.baseUrl } returns flowOf(baseUrl)
        every { tokenStore.token } returns flowOf(token)
        every { apiFactory.createFavoritesApi(any(), any()) } returns favoritesApi
    }

    @Test
    fun `shows the heart before the server answers`() = runTest {
        pairedServer()
        coEvery { favoritesApi.addFavorites(FavoriteIdsRequest(listOf(10))) } answers {
            assertEquals(true, repository.overrides.value[10])
            FavoritesAddedResponse(added = 1)
        }

        repository.setFavorite(10, favorite = true)

        assertEquals(mapOf(10 to true), repository.overrides.value)
    }

    @Test
    fun `keeps an unfavorite override after the server confirms`() = runTest {
        pairedServer()
        coEvery { favoritesApi.removeFavorites(any()) } returns FavoritesRemovedResponse(removed = 1)

        repository.setFavorite(10, favorite = false)

        assertEquals(mapOf(10 to false), repository.overrides.value)
    }

    @Test
    fun `clears the override entirely when the first write fails`() = runTest {
        pairedServer()
        coEvery { favoritesApi.addFavorites(any()) } throws IOException("unreachable")

        val error = runCatching { repository.setFavorite(10, favorite = true) }

        assertTrue(error.exceptionOrNull() is IOException)
        assertNull(repository.overrides.value[10])
        assertEquals(emptyMap<Int, Boolean>(), repository.overrides.value)
    }

    @Test
    fun `rolls back to the previous override when a later write fails`() = runTest {
        pairedServer()
        coEvery { favoritesApi.addFavorites(any()) } returns FavoritesAddedResponse(added = 1)
        coEvery { favoritesApi.removeFavorites(any()) } throws IOException("unreachable")

        repository.setFavorite(10, favorite = true)
        runCatching { repository.setFavorite(10, favorite = false) }

        assertEquals(mapOf(10 to true), repository.overrides.value)
    }

    @Test
    fun `reports a rescan rejection as staleness`() = runTest {
        pairedServer()
        coEvery { favoritesApi.addFavorites(any()) } throws HttpException(
            Response.error<Unit>(
                400,
                """{"detail":"Tracks not found: 10"}""".toResponseBody("application/json".toMediaType()),
            ),
        )

        val error = runCatching { repository.setFavorite(10, favorite = true) }

        assertTrue(error.exceptionOrNull() is StaleTrackException)
        assertEquals(emptyMap<Int, Boolean>(), repository.overrides.value)
    }

    @Test
    fun `rolls the override back when no server is paired`() = runTest {
        pairedServer(baseUrl = null)

        val error = runCatching { repository.setFavorite(10, favorite = true) }

        assertTrue(error.exceptionOrNull() is IllegalStateException)
        assertEquals(emptyMap<Int, Boolean>(), repository.overrides.value)
    }

    @Test
    fun `rolls the override back when the token is gone`() = runTest {
        pairedServer(token = null)

        val error = runCatching { repository.setFavorite(10, favorite = true) }

        assertTrue(error.exceptionOrNull() is IllegalStateException)
        assertEquals(emptyMap<Int, Boolean>(), repository.overrides.value)
    }
}
