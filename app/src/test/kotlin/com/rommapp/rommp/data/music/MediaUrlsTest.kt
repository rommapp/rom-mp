package com.rommapp.rommp.data.music

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins how a server-relative media path becomes a fetchable URL. RomM hands out
 * cover paths unencoded, so game names with spaces, accents or a hash have to be
 * escaped per segment while the slashes and the cache-busting query stay intact.
 * The base URL is stored without a trailing slash, so the leading slash of the
 * path is what joins the two. Robolectric supplies the real Uri encoder.
 */
@RunWith(RobolectricTestRunner::class)
class MediaUrlsTest {

    private val base = "https://romm.example.com"

    @Test
    fun `escapes spaces without touching the segment separators`() {
        assertEquals(
            "https://romm.example.com/assets/roms/Super%20Mario%2064/cover-l.png",
            encodedMediaUrl(base, "/assets/roms/Super Mario 64/cover-l.png"),
        )
    }

    @Test
    fun `escapes non-ascii characters`() {
        assertEquals(
            "https://romm.example.com/assets/Pok%C3%A9mon/cover.png",
            encodedMediaUrl(base, "/assets/Pokémon/cover.png"),
        )
    }

    @Test
    fun `escapes characters that would otherwise restructure the url`() {
        assertEquals(
            "https://romm.example.com/assets/Sonic%20%233/cover%2Bart.png",
            encodedMediaUrl(base, "/assets/Sonic #3/cover+art.png"),
        )
    }

    @Test
    fun `keeps the query separators readable while escaping the values`() {
        assertEquals(
            "https://romm.example.com/assets/cover.png?ts=1739&size=large",
            encodedMediaUrl(base, "/assets/cover.png?ts=1739&size=large"),
        )
        assertEquals(
            "https://romm.example.com/assets/cover.png?name=a%20b",
            encodedMediaUrl(base, "/assets/cover.png?name=a b"),
        )
    }

    @Test
    fun `splits the path from the query on the first question mark only`() {
        assertEquals(
            "https://romm.example.com/assets/cover.png?ts=1&next=a%3Fb",
            encodedMediaUrl(base, "/assets/cover.png?ts=1&next=a?b"),
        )
    }

    @Test
    fun `leaves an already safe path untouched`() {
        assertEquals(
            "https://romm.example.com/assets/roms/12/cover-l.png",
            encodedMediaUrl(base, "/assets/roms/12/cover-l.png"),
        )
    }
}
