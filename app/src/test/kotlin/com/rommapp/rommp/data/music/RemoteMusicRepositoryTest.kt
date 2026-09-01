package com.rommapp.rommp.data.music

import com.rommapp.rommp.data.auth.TokenStore
import com.rommapp.rommp.data.download.CachedTrackEntity
import com.rommapp.rommp.data.download.DownloadDao
import com.rommapp.rommp.data.remote.RommApiFactory
import com.rommapp.rommp.data.remote.RommMusicApi
import com.rommapp.rommp.data.remote.RommRomsApi
import com.rommapp.rommp.data.remote.dto.MusicTrackDto
import com.rommapp.rommp.data.remote.dto.PaginatedResponse
import com.rommapp.rommp.data.remote.dto.RomSummaryDto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * Pins the shelf and album queries. A game whose only tracks are jingles must
 * not reach the shelf with an album view that would come up empty, an album has
 * to arrive in track order with untracked rips at the end, and every album fetch
 * has to refill the offline cache.
 */
@RunWith(RobolectricTestRunner::class)
class RemoteMusicRepositoryTest {

    private val romsApi = mockk<RommRomsApi>()
    private val musicApi = mockk<RommMusicApi>()
    private val apiFactory = mockk<RommApiFactory>()
    private val tokenStore = mockk<TokenStore>()
    private val downloadDao = mockk<DownloadDao>(relaxed = true)

    private val repository = RemoteMusicRepository(apiFactory, tokenStore, downloadDao)

    @Before
    fun setUp() {
        every { tokenStore.baseUrl } returns flowOf("https://romm.example.com")
        every { tokenStore.token } returns flowOf("rmm_secret")
        every { apiFactory.createRomsApi(any(), any()) } returns romsApi
        every { apiFactory.createMusicApi(any(), any()) } returns musicApi
    }

    private fun rom(id: Int, coverPath: String? = null) = RomSummaryDto(
        id = id,
        name = "Game $id",
        platformSlug = "n64",
        platformName = "Nintendo 64",
        pathCoverLarge = coverPath,
        hasSoundtrack = true,
    )

    private fun trackDto(romId: Int, track: Int?, title: String) = MusicTrackDto(
        romFileId = romId * 100 + (track ?: 0),
        romId = romId,
        title = title,
        track = track,
        durationSeconds = 200.0,
        platformId = 4,
        platformSlug = "n64",
        platformName = "Nintendo 64",
        streamUrl = "/api/music/tracks/$romId/stream/$title.mp3",
    )

    private fun tracksPage(vararg items: MusicTrackDto) = PaginatedResponse(items = items.toList())

    @Test
    fun `keeps only the games that have a track worth showing`() = runTest {
        coEvery { romsApi.getRoms(any(), any(), any(), any(), any(), any()) } returns
            PaginatedResponse(items = listOf(rom(1), rom(2), rom(3)))
        coEvery { musicApi.getTracks(romId = null, minDuration = any(), limit = any(), offset = any()) } returns
            tracksPage(trackDto(1, 1, "Overworld"), trackDto(3, 1, "Title"))

        val games = repository.gamesWithMusic()

        assertEquals(listOf(1, 3), games.map { it.romId })
    }

    @Test
    fun `encodes the box art onto the shelf entry`() = runTest {
        coEvery { romsApi.getRoms(any(), any(), any(), any(), any(), any()) } returns
            PaginatedResponse(items = listOf(rom(1, coverPath = "/assets/roms/Super Mario 64/cover-l.png")))
        coEvery { musicApi.getTracks(romId = null, minDuration = any(), limit = any(), offset = any()) } returns
            tracksPage(trackDto(1, 1, "Overworld"))

        val game = repository.gamesWithMusic().single()

        assertEquals(
            "https://romm.example.com/assets/roms/Super%20Mario%2064/cover-l.png",
            game.coverUrl,
        )
    }

    @Test
    fun `leaves the shelf entry without art when the game has none`() = runTest {
        coEvery { romsApi.getRoms(any(), any(), any(), any(), any(), any()) } returns
            PaginatedResponse(items = listOf(rom(1)))
        coEvery { musicApi.getTracks(romId = null, minDuration = any(), limit = any(), offset = any()) } returns
            tracksPage(trackDto(1, 1, "Overworld"))

        assertNull(repository.gamesWithMusic().single().coverUrl)
    }

    @Test
    fun `orders an album by track number with untracked rips last`() = runTest {
        coEvery { musicApi.getTracks(romId = 1, minDuration = any(), limit = any(), offset = any()) } returns
            tracksPage(
                trackDto(1, 3, "Ending"),
                trackDto(1, null, "Unknown"),
                trackDto(1, 1, "Title"),
            )
        coEvery { romsApi.getRom(1) } returns rom(1)

        val titles = repository.gameTracks(romId = 1).map { it.title }

        assertEquals(listOf("Title", "Ending", "Unknown"), titles)
    }

    @Test
    fun `drops rows the server attributed to another game`() = runTest {
        coEvery { musicApi.getTracks(romId = 1, minDuration = any(), limit = any(), offset = any()) } returns
            tracksPage(trackDto(1, 1, "Title"), trackDto(2, 1, "Foreign"))
        coEvery { romsApi.getRom(1) } returns rom(1)

        assertEquals(listOf("Title"), repository.gameTracks(romId = 1).map { it.title })
    }

    @Test
    fun `caches the album so it can be listed offline`() = runTest {
        coEvery { musicApi.getTracks(romId = 1, minDuration = any(), limit = any(), offset = any()) } returns
            tracksPage(trackDto(1, 1, "Title"))
        coEvery { romsApi.getRom(1) } returns rom(1, coverPath = "/assets/cover.png")
        val cached = slot<List<CachedTrackEntity>>()
        coEvery { downloadDao.upsertCachedTracks(capture(cached)) } returns Unit

        repository.gameTracks(romId = 1)

        val entity = cached.captured.single()
        assertEquals(1, entity.gameRomId)
        assertEquals("Title", entity.title)
        assertEquals("https://romm.example.com/assets/cover.png", entity.gameCoverUrl)
    }

    @Test
    fun `still returns the album when the box art lookup fails`() = runTest {
        coEvery { musicApi.getTracks(romId = 1, minDuration = any(), limit = any(), offset = any()) } returns
            tracksPage(trackDto(1, 1, "Title"))
        coEvery { romsApi.getRom(1) } throws IOException("unreachable")

        val track = repository.gameTracks(romId = 1).single()

        assertEquals("Title", track.title)
        assertNull(track.gameCoverUrl)
    }
}
