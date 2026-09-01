package com.rommapp.rommp.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * Persists the RomM connection: server base URL and the rmm_ client token
 * obtained from the device-auth flow. Username/password is never stored.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.authDataStore

    val baseUrl: Flow<String?> = store.data.map { it[KEY_BASE_URL] }
    val token: Flow<String?> = store.data.map { it[KEY_TOKEN] }
    val deviceId: Flow<String?> = store.data.map { it[KEY_DEVICE_ID] }
    val isAuthenticated: Flow<Boolean> = token.map { !it.isNullOrBlank() }

    /** Scopes the server granted at pairing (may lag [AuthRepository]'s requested set). */
    val grantedScopes: Flow<Set<String>> = store.data.map { it[KEY_SCOPES] ?: emptySet() }

    /** Whether the stored token was granted [scope]; for gating UI on re-pair-only scopes. */
    fun hasScope(scope: String): Flow<Boolean> = grantedScopes.map { scope in it }

    suspend fun save(baseUrl: String, token: String, deviceId: String, scopes: List<String> = emptyList()) {
        store.edit {
            it[KEY_BASE_URL] = baseUrl
            it[KEY_TOKEN] = token
            it[KEY_DEVICE_ID] = deviceId
            it[KEY_SCOPES] = scopes.toSet()
        }
    }

    suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        val KEY_SCOPES = stringSetPreferencesKey("granted_scopes")
    }
}
