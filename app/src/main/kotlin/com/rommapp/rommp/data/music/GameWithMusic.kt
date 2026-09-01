package com.rommapp.rommp.data.music

/** A game that has a soundtrack, for the library shelf. */
data class GameWithMusic(
    val romId: Int,
    val name: String,
    val platformName: String,
    /** Absolute cover-art URL (record-case art), or null when the game has none. */
    val coverUrl: String?,
)
