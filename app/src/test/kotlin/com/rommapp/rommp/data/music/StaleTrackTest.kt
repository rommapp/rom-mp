package com.rommapp.rommp.data.music

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Pins the one server rejection that means "your track ids are out of date".
 * A rescan reissues romFileIds, and the resulting 400 has to become a
 * refresh-and-retry prompt rather than a raw HTTP error; every other failure has
 * to reach the caller untouched, or real breakage would be reported as staleness.
 */
class StaleTrackTest {

    private val events = mockk<StaleTrackEvents>(relaxed = true)

    private fun httpError(code: Int, body: String) = HttpException(
        Response.error<Unit>(code, body.toResponseBody("application/json".toMediaType())),
    )

    @Test
    fun `maps a rescan rejection to a stale track error`() {
        val original = httpError(400, """{"detail":"Tracks not found: 12, 13"}""")

        val mapped = original.mapStaleTrackError(events)

        assertTrue(mapped is StaleTrackException)
        assertSame(original, mapped.cause)
    }

    @Test
    fun `announces staleness so open track lists can refetch`() {
        httpError(400, """{"detail":"Tracks not found"}""").mapStaleTrackError(events)

        verify(exactly = 1) { events.notifyStale() }
    }

    @Test
    fun `leaves an unrelated bad request alone`() {
        val original = httpError(400, """{"detail":"Playlist name already taken"}""")

        assertSame(original, original.mapStaleTrackError(events))
        verify(exactly = 0) { events.notifyStale() }
    }

    @Test
    fun `leaves the same message on a different status code alone`() {
        val original = httpError(404, """{"detail":"Tracks not found"}""")

        assertSame(original, original.mapStaleTrackError(events))
        verify(exactly = 0) { events.notifyStale() }
    }

    @Test
    fun `leaves a transport failure alone`() {
        val original = IOException("unreachable")

        assertSame(original, original.mapStaleTrackError(events))
        verify(exactly = 0) { events.notifyStale() }
    }

    @Test
    fun `delivers the staleness broadcast to a listening screen`() = runTest {
        val broadcast = StaleTrackEvents()
        val received = async(start = CoroutineStart.UNDISPATCHED) { broadcast.events.first() }

        broadcast.notifyStale()

        assertEquals(Unit, received.await())
    }
}
