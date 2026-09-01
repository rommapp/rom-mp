package com.rommapp.rommp.data.music

import com.rommapp.rommp.data.remote.dto.MusicTrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins the fallbacks a server row goes through on its way to the player. RomM
 * leaves the title empty for untagged rips and the platform display name empty
 * for platforms it only knows by slug, and both of those land in the UI, so the
 * substitutes are asserted rather than assumed.
 */
@RunWith(RobolectricTestRunner::class)
class TrackMappingTest {

    private val base = "https://romm.example.com"

    private fun dto(
        title: String? = "Overworld",
        gameName: String? = "Ocarina of Time",
        platformName: String = "Nintendo 64",
        platformSlug: String = "n64",
        streamUrl: String = "/api/music/tracks/12/stream/theme.mp3",
        coverUrl: String? = null,
        track: Int? = 3,
        isFavorite: Boolean = false,
    ) = MusicTrackDto(
        romFileId = 12,
        romId = 7,
        title = title,
        track = track,
        durationSeconds = 214.5,
        isFavorite = isFavorite,
        gameName = gameName,
        platformId = 4,
        platformSlug = platformSlug,
        platformName = platformName,
        streamUrl = streamUrl,
        coverUrl = coverUrl,
    )

    @Test
    fun `carries the identifying fields through unchanged`() {
        val track = dto().toTrack(base)

        assertEquals(12, track.romFileId)
        assertEquals(7, track.romId)
        assertEquals("Overworld", track.title)
        assertEquals("Ocarina of Time", track.gameName)
        assertEquals("Nintendo 64", track.platformName)
        assertEquals(3, track.trackNumber)
        assertEquals(214.5, track.durationSeconds!!, 0.0)
    }

    @Test
    fun `joins the stream path onto the server base url`() {
        assertEquals(
            "https://romm.example.com/api/music/tracks/12/stream/theme.mp3",
            dto().toTrack(base).streamUrl,
        )
    }

    @Test
    fun `falls back to the stream file name when the title is blank`() {
        assertEquals("theme.mp3", dto(title = "").toTrack(base).title)
        assertEquals("theme.mp3", dto(title = "   ").toTrack(base).title)
    }

    @Test
    fun `falls back to the stream file name when the title is missing`() {
        assertEquals("theme.mp3", dto(title = null).toTrack(base).title)
    }

    @Test
    fun `falls back to the platform slug when the display name is blank`() {
        assertEquals("n64", dto(platformName = "").toTrack(base).platformName)
    }

    @Test
    fun `treats a missing game name as empty rather than null`() {
        assertEquals("", dto(gameName = null).toTrack(base).gameName)
    }

    @Test
    fun `encodes the cover path onto the base url`() {
        val track = dto(coverUrl = "/assets/roms/Super Mario 64/cover-l.png").toTrack(base)

        assertEquals(
            "https://romm.example.com/assets/roms/Super%20Mario%2064/cover-l.png",
            track.coverUrl,
        )
    }

    @Test
    fun `leaves the cover null when the server has none`() {
        assertNull(dto(coverUrl = null).toTrack(base).coverUrl)
    }

    @Test
    fun `carries the favorite flag`() {
        assertTrue(dto(isFavorite = true).toTrack(base).isFavorite)
    }
}
