package com.rommapp.rommp.data.music

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Pins the optimistic add-to-crate index. The sheet renders its checkmarks and
 * counts from this state before the server has answered, so a failed write has
 * to leave the index exactly as it found it, and a repeated toggle must not
 * drift the count.
 */
class DefaultCrateMembershipRepositoryTest {

    private val playlists = mockk<PlaylistsRepository>()
    private val repository = DefaultCrateMembershipRepository(playlists)

    private fun crate(id: Int, trackCount: Int = 0, isOwned: Boolean = true) = Playlist(
        id = id,
        name = "Crate $id",
        description = null,
        isPublic = false,
        ownerUsername = "nendo",
        trackCount = trackCount,
        isOwned = isOwned,
    )

    private fun track(romFileId: Int) = Track(
        romFileId = romFileId,
        romId = 7,
        title = "Overworld",
        gameName = "Ocarina of Time",
        platformName = "Nintendo 64",
        trackNumber = 1,
        durationSeconds = 120.0,
        streamUrl = "https://romm.example.com/stream/$romFileId",
        coverUrl = null,
    )

    @Test
    fun `indexes only the crates the user owns`() = runTest {
        coEvery { playlists.playlists() } returns listOf(crate(1), crate(2, isOwned = false))
        coEvery { playlists.playlistTrackRefs(1, any()) } returns listOf(TrackRef(10), TrackRef(11))

        repository.refresh()

        assertEquals(listOf(1), repository.ownedCrates.value.map { it.id })
        assertEquals(mapOf(10 to setOf(1), 11 to setOf(1)), repository.membership.value)
    }

    @Test
    fun `records a track that sits in several crates`() = runTest {
        coEvery { playlists.playlists() } returns listOf(crate(1), crate(2))
        coEvery { playlists.playlistTrackRefs(1, any()) } returns listOf(TrackRef(10))
        coEvery { playlists.playlistTrackRefs(2, any()) } returns listOf(TrackRef(10))

        repository.refresh()

        assertEquals(mapOf(10 to setOf(1, 2)), repository.membership.value)
    }

    @Test
    fun `shows the track as added before the server answers`() = runTest {
        coEvery { playlists.playlists() } returns listOf(crate(1, trackCount = 4))
        coEvery { playlists.playlistTrackRefs(1, any()) } returns emptyList()
        repository.refresh()
        coEvery { playlists.addTracks(1, listOf(10)) } answers {
            assertTrue(1 in repository.membership.value.getValue(10))
            assertEquals(5, repository.ownedCrates.value.single().trackCount)
            1
        }

        repository.toggleCrate(track(10), playlistId = 1, add = true)

        assertEquals(setOf(1), repository.membership.value[10])
        assertEquals(5, repository.ownedCrates.value.single().trackCount)
    }

    @Test
    fun `restores the index when the add fails`() = runTest {
        coEvery { playlists.playlists() } returns listOf(crate(1, trackCount = 4))
        coEvery { playlists.playlistTrackRefs(1, any()) } returns emptyList()
        repository.refresh()
        coEvery { playlists.addTracks(1, listOf(10)) } throws IOException("unreachable")

        val error = runCatching { repository.toggleCrate(track(10), playlistId = 1, add = true) }

        assertTrue(error.exceptionOrNull() is IOException)
        assertEquals(emptyMap<Int, Set<Int>>(), repository.membership.value)
        assertEquals(4, repository.ownedCrates.value.single().trackCount)
    }

    @Test
    fun `restores the index when the removal fails`() = runTest {
        coEvery { playlists.playlists() } returns listOf(crate(1, trackCount = 4))
        coEvery { playlists.playlistTrackRefs(1, any()) } returns listOf(TrackRef(10))
        repository.refresh()
        coEvery { playlists.removeTracks(1, listOf(10)) } throws IOException("unreachable")

        val error = runCatching { repository.toggleCrate(track(10), playlistId = 1, add = false) }

        assertTrue(error.exceptionOrNull() is IOException)
        assertEquals(mapOf(10 to setOf(1)), repository.membership.value)
        assertEquals(4, repository.ownedCrates.value.single().trackCount)
    }

    @Test
    fun `drops the track key once it leaves its last crate`() = runTest {
        coEvery { playlists.playlists() } returns listOf(crate(1, trackCount = 1))
        coEvery { playlists.playlistTrackRefs(1, any()) } returns listOf(TrackRef(10))
        coEvery { playlists.removeTracks(1, listOf(10)) } returns 1
        repository.refresh()

        repository.toggleCrate(track(10), playlistId = 1, add = false)

        assertEquals(emptyMap<Int, Set<Int>>(), repository.membership.value)
        assertEquals(0, repository.ownedCrates.value.single().trackCount)
    }

    @Test
    fun `does not move the count when the track is already in that state`() = runTest {
        coEvery { playlists.playlists() } returns listOf(crate(1, trackCount = 3))
        coEvery { playlists.playlistTrackRefs(1, any()) } returns listOf(TrackRef(10))
        coEvery { playlists.addTracks(1, listOf(10)) } returns 0
        repository.refresh()

        repository.toggleCrate(track(10), playlistId = 1, add = true)

        assertEquals(mapOf(10 to setOf(1)), repository.membership.value)
        assertEquals(3, repository.ownedCrates.value.single().trackCount)
    }

    @Test
    fun `never lets a count fall below zero`() = runTest {
        coEvery { playlists.playlists() } returns listOf(crate(1, trackCount = 0))
        coEvery { playlists.playlistTrackRefs(1, any()) } returns listOf(TrackRef(10))
        coEvery { playlists.removeTracks(1, listOf(10)) } returns 1
        repository.refresh()

        repository.toggleCrate(track(10), playlistId = 1, add = false)

        assertEquals(0, repository.ownedCrates.value.single().trackCount)
    }

    @Test
    fun `shows a just created crate before the next refresh`() = runTest {
        repository.registerCrate(crate(9))
        repository.registerCrate(crate(9))

        assertEquals(listOf(9), repository.ownedCrates.value.map { it.id })
    }
}
