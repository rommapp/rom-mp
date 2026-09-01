package com.rommapp.rommp.data.remote

import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Pins what actually leaves the device. The pairing endpoints are
 * unauthenticated and must stay that way, the music clients must carry the
 * Bearer token on every call, and a server behind a reverse proxy subpath must
 * still resolve its endpoints under that prefix.
 */
class RommApiAuthTest {

    private lateinit var server: MockWebServer
    private lateinit var factory: RommApiFactory

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        factory = RommApiFactory(OkHttpClient(), Moshi.Builder().build())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun origin() = "http://${server.hostName}:${server.port}"

    @Test
    fun `attaches the bearer token to an authenticated client`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":1,"username":"nendo"}"""))

        factory.createUsersApi(origin(), "rmm_secret").getCurrentUser()

        val request = server.takeRequest()
        assertEquals("Bearer rmm_secret", request.getHeader("Authorization"))
        assertEquals("/api/users/me", request.path)
    }

    @Test
    fun `sends no authorization header on the pairing client`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))

        factory.create(origin()).heartbeat()

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertEquals("/api/heartbeat", request.path)
    }

    @Test
    fun `resolves endpoints when the address was typed with a trailing slash`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":1,"username":"nendo"}"""))

        factory.createUsersApi("${origin()}/", "rmm_secret").getCurrentUser()

        assertEquals("/api/users/me", server.takeRequest().path)
    }

    @Test
    fun `keeps a reverse proxy subpath in front of every endpoint`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":1,"username":"nendo"}"""))

        factory.createUsersApi("${origin()}/romm", "rmm_secret").getCurrentUser()

        assertEquals("/romm/api/users/me", server.takeRequest().path)
    }
}
