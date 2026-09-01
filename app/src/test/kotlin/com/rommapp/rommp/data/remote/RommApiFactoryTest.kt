package com.rommapp.rommp.data.remote

import com.rommapp.rommp.data.remote.RommApiFactory.Companion.normalizeBaseUrl
import com.rommapp.rommp.data.remote.RommApiFactory.Companion.normalizeOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the canonical form of the user-typed server address. The playback resolver
 * compares this origin against every stream URI before it attaches the Bearer
 * token, so a normalization that drops the scheme, keeps a trailing slash, or
 * leaves whitespace in place either breaks streaming or widens what counts as
 * the paired server.
 */
class RommApiFactoryTest {

    @Test
    fun `assumes https for a bare host`() {
        assertEquals("https://romm.example.com", normalizeOrigin("romm.example.com"))
    }

    @Test
    fun `assumes https for a bare host with a port`() {
        assertEquals("https://romm.example.com:8443", normalizeOrigin("romm.example.com:8443"))
    }

    @Test
    fun `keeps an explicit http scheme instead of upgrading it`() {
        assertEquals("http://192.168.1.5:8080", normalizeOrigin("http://192.168.1.5:8080"))
    }

    @Test
    fun `keeps an explicit https scheme`() {
        assertEquals("https://romm.example.com", normalizeOrigin("https://romm.example.com"))
    }

    @Test
    fun `strips the trailing slash`() {
        assertEquals("https://romm.example.com", normalizeOrigin("https://romm.example.com/"))
        assertEquals("https://romm.example.com", normalizeOrigin("romm.example.com/"))
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertEquals("https://romm.example.com", normalizeOrigin("  romm.example.com  "))
        assertEquals("https://romm.example.com", normalizeOrigin("\thttps://romm.example.com/\n"))
    }

    @Test
    fun `keeps a subpath for servers behind a reverse proxy`() {
        assertEquals("https://example.com/romm", normalizeOrigin("https://example.com/romm/"))
    }

    @Test
    fun `base url ends in exactly one slash whatever the input`() {
        assertEquals("https://romm.example.com/", normalizeBaseUrl("romm.example.com"))
        assertEquals("https://romm.example.com/", normalizeBaseUrl("https://romm.example.com"))
        assertEquals("https://romm.example.com/", normalizeBaseUrl("https://romm.example.com/"))
        assertEquals("http://192.168.1.5:8080/", normalizeBaseUrl(" http://192.168.1.5:8080/ "))
    }
}
