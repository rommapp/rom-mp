package com.rommapp.rommp.data.playback

import com.rommapp.rommp.data.download.CachedTrackEntity
import com.rommapp.rommp.data.download.DownloadDao
import com.rommapp.rommp.data.download.DownloadedTrackEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins how a persisted queue is rebuilt on relaunch. The saved ids carry the
 * listening order, the database returns rows in whatever order it likes, and a
 * downloaded copy has to win over the stream URL or an offline restore plays
 * nothing.
 */
class PlaybackQueueResolverTest {

    private val dao = mockk<DownloadDao>()
    private val resolver = PlaybackQueueResolver(dao)

    private fun cached(id: Int, streamUrl: String = "https://romm.example.com/stream/$id") =
        CachedTrackEntity(
            romFileId = id,
            gameRomId = 7,
            title = "Track $id",
            gameName = "Ocarina of Time",
            platformName = "Nintendo 64",
            trackNumber = id,
            durationSeconds = 120.0,
            streamUrl = streamUrl,
            coverUrl = null,
        )

    private fun downloaded(id: Int, audioUri: String) = DownloadedTrackEntity(
        romFileId = id,
        gameRomId = 7,
        title = "Track $id",
        gameName = "Ocarina of Time",
        trackNumber = id,
        durationSeconds = 120.0,
        audioUri = audioUri,
    )

    @Test
    fun `resolves nothing for an empty queue without querying the database`() = runTest {
        assertEquals(emptyList<Any>(), resolver.resolve(emptyList()))

        coVerify(exactly = 0) { dao.cachedTracksByFileIds(any()) }
        coVerify(exactly = 0) { dao.downloadedTracksByFileIds(any()) }
    }

    @Test
    fun `keeps the saved order rather than the database order`() = runTest {
        coEvery { dao.cachedTracksByFileIds(any()) } returns listOf(cached(2), cached(3), cached(1))
        coEvery { dao.downloadedTracksByFileIds(any()) } returns emptyList()

        val tracks = resolver.resolve(listOf(3, 1, 2))

        assertEquals(listOf(3, 1, 2), tracks.map { it.romFileId })
    }

    @Test
    fun `drops ids the cache no longer knows`() = runTest {
        coEvery { dao.cachedTracksByFileIds(any()) } returns listOf(cached(1), cached(3))
        coEvery { dao.downloadedTracksByFileIds(any()) } returns emptyList()

        val tracks = resolver.resolve(listOf(1, 2, 3))

        assertEquals(listOf(1, 3), tracks.map { it.romFileId })
    }

    @Test
    fun `plays the downloaded copy in place of the stream url`() = runTest {
        coEvery { dao.cachedTracksByFileIds(any()) } returns listOf(cached(1), cached(2))
        coEvery { dao.downloadedTracksByFileIds(any()) } returns
            listOf(downloaded(2, "content://media/external/audio/media/99"))

        val tracks = resolver.resolve(listOf(1, 2))

        assertEquals("https://romm.example.com/stream/1", tracks[0].streamUrl)
        assertEquals("content://media/external/audio/media/99", tracks[1].streamUrl)
    }

    @Test
    fun `carries the cached metadata onto the restored track`() = runTest {
        coEvery { dao.cachedTracksByFileIds(any()) } returns listOf(cached(1))
        coEvery { dao.downloadedTracksByFileIds(any()) } returns emptyList()

        val track = resolver.resolve(listOf(1)).single()

        assertEquals(7, track.romId)
        assertEquals("Track 1", track.title)
        assertEquals("Ocarina of Time", track.gameName)
        assertEquals("Nintendo 64", track.platformName)
        assertEquals(1, track.trackNumber)
    }
}
