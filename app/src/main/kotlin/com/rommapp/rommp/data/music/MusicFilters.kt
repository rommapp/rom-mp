package com.rommapp.rommp.data.music

/** Query parameters for the music track list. Nulls are omitted from the request. */
data class MusicFilters(
    val search: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val platformIds: List<Int>? = null,
    val year: Int? = null,
    val minDuration: Double? = null,
    val maxDuration: Double? = null,
    val orderBy: String = "title",
    val orderDir: String = "asc",
    val limit: Int = 50,
    val offset: Int = 0,
)
