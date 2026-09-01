package com.rommapp.rommp.data.music

/** A user-assembled track collection ("crate" in the UI). */
data class Playlist(
    val id: Int,
    val name: String,
    val description: String?,
    val isPublic: Boolean,
    val ownerUsername: String,
    val trackCount: Int,
    /** True when the authed user owns it; others' public playlists are read-only. */
    val isOwned: Boolean,
)
