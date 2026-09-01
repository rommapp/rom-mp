package com.rommapp.rommp.data.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A game can hold several soundtracks, so the box art is the album identity and
 * the embedded per-track art is only the stand-in. Pins that order, which drives
 * the album grid, the vinyl and the mini player.
 */
class TrackTest {

    private fun track(gameCoverUrl: String?, coverUrl: String?) = Track(
        romFileId = 12,
        romId = 7,
        title = "Overworld",
        gameName = "Ocarina of Time",
        platformName = "Nintendo 64",
        trackNumber = 1,
        durationSeconds = 120.0,
        streamUrl = "https://romm.example.com/stream/12",
        coverUrl = coverUrl,
        gameCoverUrl = gameCoverUrl,
    )

    @Test
    fun `prefers the game box art`() {
        assertEquals("box.png", track(gameCoverUrl = "box.png", coverUrl = "embedded.png").albumCoverUrl)
    }

    @Test
    fun `falls back to the embedded track art`() {
        assertEquals("embedded.png", track(gameCoverUrl = null, coverUrl = "embedded.png").albumCoverUrl)
    }

    @Test
    fun `has no cover when the server knows neither`() {
        assertNull(track(gameCoverUrl = null, coverUrl = null).albumCoverUrl)
    }
}
