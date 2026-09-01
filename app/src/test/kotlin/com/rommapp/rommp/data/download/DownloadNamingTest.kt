package com.rommapp.rommp.data.download

import android.webkit.MimeTypeMap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Pins the names a download lands under in the public Music collection. Every
 * input here comes from the server (stream URL, track title, game and platform
 * names), and the extension in particular reaches the MediaStore display name
 * unescaped, so the allowlist that keeps a percent-encoded separator out of it
 * is asserted directly. Robolectric supplies the real Uri decoder and MIME map.
 */
@RunWith(RobolectricTestRunner::class)
class DownloadNamingTest {

    @Test
    fun `takes the extension from the stream url file name`() {
        assertEquals("flac", sourceExtension("https://romm.example.com/api/music/theme.flac"))
    }

    @Test
    fun `lowercases the extension`() {
        assertEquals("flac", sourceExtension("https://romm.example.com/api/music/THEME.FLAC"))
    }

    @Test
    fun `ignores the query string`() {
        assertEquals("ogg", sourceExtension("https://romm.example.com/api/music/theme.ogg?ts=1739"))
    }

    @Test
    fun `falls back to mp3 when a percent-encoded separator decodes into the extension`() {
        assertEquals("mp3", sourceExtension("https://romm.example.com/api/music/track.m%2Fp3"))
        assertEquals("mp3", sourceExtension("https://romm.example.com/api/music/track.%2E%2E%2Fx"))
    }

    @Test
    fun `falls back to mp3 for a missing or oversized extension`() {
        assertEquals("mp3", sourceExtension("https://romm.example.com/api/music/theme"))
        assertEquals("mp3", sourceExtension("https://romm.example.com/api/music/theme."))
        assertEquals("mp3", sourceExtension("https://romm.example.com/api/music/theme.soundtrack"))
    }

    @Test
    fun `falls back to mp3 when the extension holds a space`() {
        assertEquals("mp3", sourceExtension("https://romm.example.com/api/music/theme.m%20p3"))
    }

    @Test
    fun `replaces the characters android refuses in a name`() {
        assertEquals("Zelda_ Ocarina", sanitizeName("Zelda: Ocarina"))
        assertEquals("a_b_c_d_e_f_g_h_i", sanitizeName("a/b\\c:d*e?f\"g<h>i"))
    }

    @Test
    fun `never yields a blank name`() {
        assertEquals("track", sanitizeName(""))
        assertEquals("track", sanitizeName("   "))
        assertEquals("track", sanitizeName("..."))
    }

    @Test
    fun `strips the dots and spaces a name could climb out on`() {
        assertEquals("_evil", sanitizeName("../evil"))
        assertEquals("Overworld", sanitizeName("  Overworld  "))
        assertEquals("Vol. 1", sanitizeName("Vol. 1."))
    }

    @Test
    fun `numbers a file name when the server knows the track position`() {
        assertEquals("03 - Overworld.mp3", downloadFileName(3, "Overworld", "mp3"))
        assertEquals("12 - Overworld.flac", downloadFileName(12, "Overworld", "flac"))
        assertEquals("103 - Overworld.mp3", downloadFileName(103, "Overworld", "mp3"))
    }

    @Test
    fun `leaves a file name unnumbered when the track position is unknown`() {
        assertEquals("Overworld.mp3", downloadFileName(null, "Overworld", "mp3"))
    }

    @Test
    fun `sanitizes the title inside a file name`() {
        assertEquals("01 - Battle_ Final.mp3", downloadFileName(1, "Battle: Final", "mp3"))
    }

    @Test
    fun `files an album under its platform`() {
        assertEquals(
            "Music/RomM/Nintendo 64/Ocarina of Time",
            downloadRelativePath("Nintendo 64", "Ocarina of Time"),
        )
    }

    @Test
    fun `drops the platform folder when the platform is unknown`() {
        assertEquals("Music/RomM/Ocarina of Time", downloadRelativePath("", "Ocarina of Time"))
        assertEquals("Music/RomM/Ocarina of Time", downloadRelativePath("   ", "Ocarina of Time"))
    }

    @Test
    fun `keeps a name from adding a folder level of its own`() {
        assertEquals("Music/RomM/N_64/Sub_Game", downloadRelativePath("N/64", "Sub/Game"))
        assertEquals("Music/RomM/_evil", downloadRelativePath("", "../evil"))
    }

    @Test
    fun `reads the mime type from the platform map`() {
        shadowOf(MimeTypeMap.getSingleton()).addExtensionMimeTypeMapping("flac", "audio/flac")

        assertEquals("audio/flac", audioMimeType("flac"))
    }

    @Test
    fun `falls back to audio mpeg for an extension the platform does not know`() {
        assertEquals("audio/mpeg", audioMimeType("zzz"))
    }
}
