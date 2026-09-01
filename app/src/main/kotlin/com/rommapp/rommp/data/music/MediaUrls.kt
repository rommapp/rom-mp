package com.rommapp.rommp.data.music

import android.net.Uri

/**
 * Joins a server-relative media path (unencoded on the wire, e.g. cover paths
 * with spaces and a `?ts=` query) onto the base URL, percent-encoding each path
 * segment and the query while preserving the URL structure.
 */
internal fun encodedMediaUrl(baseUrl: String, path: String): String {
    val parts = path.split('?', limit = 2)
    val encodedPath = parts[0].split('/').joinToString("/") { Uri.encode(it) }
    val query = parts.getOrNull(1)?.let { "?" + Uri.encode(it, "=&") }.orEmpty()
    return baseUrl + encodedPath + query
}
