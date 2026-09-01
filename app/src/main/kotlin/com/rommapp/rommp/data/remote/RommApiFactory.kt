package com.rommapp.rommp.data.remote

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds RomM API clients bound to a specific server base URL. The base URL is
 * user-supplied at pairing time, so instances are created on demand. Device-auth
 * calls are unauthenticated; the music API attaches the Bearer token.
 */
@Singleton
class RommApiFactory @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
) {
    /** Unauthenticated client for the device-auth pairing flow. */
    fun create(baseUrl: String): RommApi =
        buildRetrofit(baseUrl, token = null).create(RommApi::class.java)

    /** Authenticated client for the music API. */
    fun createMusicApi(baseUrl: String, token: String): RommMusicApi =
        buildRetrofit(baseUrl, token).create(RommMusicApi::class.java)

    /** Authenticated client for the general ROMs list. */
    fun createRomsApi(baseUrl: String, token: String): RommRomsApi =
        buildRetrofit(baseUrl, token).create(RommRomsApi::class.java)

    /** Authenticated client for the music favorites API. */
    fun createFavoritesApi(baseUrl: String, token: String): RommFavoritesApi =
        buildRetrofit(baseUrl, token).create(RommFavoritesApi::class.java)

    /** Authenticated client for the music playlists API. */
    fun createPlaylistsApi(baseUrl: String, token: String): RommPlaylistsApi =
        buildRetrofit(baseUrl, token).create(RommPlaylistsApi::class.java)

    /** Authenticated client for the users API (identity lookup). */
    fun createUsersApi(baseUrl: String, token: String): RommUsersApi =
        buildRetrofit(baseUrl, token).create(RommUsersApi::class.java)

    private fun buildRetrofit(baseUrl: String, token: String?): Retrofit {
        val client = if (token == null) {
            okHttpClient
        } else {
            okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(request)
                }
                .build()
        }
        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    companion object {
        /** Retrofit base URL: the canonical origin with the trailing slash Retrofit requires. */
        internal fun normalizeBaseUrl(raw: String): String = normalizeOrigin(raw) + "/"

        /** Canonical server origin: trimmed, no trailing slash, https assumed for bare hosts. */
        fun normalizeOrigin(raw: String): String {
            val trimmed = raw.trim().removeSuffix("/")
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
    }
}
