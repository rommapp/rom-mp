package com.rommapp.rommp.data.playback

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pins where the Bearer token is allowed to travel. The MediaSession takes URIs
 * from outside controllers and HTTP redirects can point anywhere, so the token
 * must reach the paired origin and nothing else. Robolectric supplies the real
 * android.net.Uri; the stubbed one on the plain unit-test classpath would make
 * every host comparison null == null.
 */
@RunWith(RobolectricTestRunner::class)
class StreamAuthHeadersTest {

    private val paired = "https://romm.example.com"
    private val token = "rmm_secret"

    private fun headersFor(uri: String, baseUrl: String? = paired, storedToken: String? = token) =
        streamAuthHeaders(Uri.parse(uri)) { baseUrl to storedToken }

    @Test
    fun `attaches the bearer token to the paired origin`() {
        assertEquals(
            mapOf("Authorization" to "Bearer rmm_secret"),
            headersFor("https://romm.example.com/api/music/tracks/12/stream"),
        )
    }

    @Test
    fun `attaches the token whatever the path and query`() {
        assertEquals(
            mapOf("Authorization" to "Bearer rmm_secret"),
            headersFor("https://romm.example.com/api/music/tracks/12/stream?ts=99"),
        )
    }

    @Test
    fun `withholds the token from a different host`() {
        assertEquals(emptyMap<String, String>(), headersFor("https://evil.example.com/api/music/stream"))
    }

    @Test
    fun `withholds the token from a subdomain of the paired host`() {
        assertEquals(emptyMap<String, String>(), headersFor("https://romm.example.com.evil.test/stream"))
    }

    @Test
    fun `withholds the token from a different port`() {
        val headers = streamAuthHeaders(Uri.parse("https://romm.example.com:9090/stream")) {
            "https://romm.example.com:8443" to token
        }

        assertEquals(emptyMap<String, String>(), headers)
    }

    @Test
    fun `attaches the token when the port matches explicitly`() {
        val headers = streamAuthHeaders(Uri.parse("https://romm.example.com:8443/stream")) {
            "https://romm.example.com:8443" to token
        }

        assertEquals(mapOf("Authorization" to "Bearer rmm_secret"), headers)
    }

    @Test
    fun `withholds the token when a redirect downgrades the scheme`() {
        assertEquals(emptyMap<String, String>(), headersFor("http://romm.example.com/api/music/stream"))
    }

    @Test
    fun `withholds the token when an implicit port is written out`() {
        assertEquals(emptyMap<String, String>(), headersFor("https://romm.example.com:443/stream"))
    }

    @Test
    fun `leaves a content uri alone without reading the token`() {
        val headers = streamAuthHeaders(Uri.parse("content://media/external/audio/media/42")) {
            fail("credentials must not be read for a local uri")
            error("unreachable")
        }

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `leaves a file uri alone without reading the token`() {
        val headers = streamAuthHeaders(Uri.parse("file:///storage/emulated/0/Music/theme.mp3")) {
            fail("credentials must not be read for a local uri")
            error("unreachable")
        }

        assertTrue(headers.isEmpty())
    }

    @Test
    fun `sends no header when nothing is paired`() {
        assertEquals(
            emptyMap<String, String>(),
            headersFor("https://romm.example.com/stream", baseUrl = null),
        )
    }

    @Test
    fun `sends no header when the token is missing or blank`() {
        val url = "https://romm.example.com/stream"
        assertEquals(emptyMap<String, String>(), headersFor(url, storedToken = null))
        assertEquals(emptyMap<String, String>(), headersFor(url, storedToken = "   "))
    }
}
