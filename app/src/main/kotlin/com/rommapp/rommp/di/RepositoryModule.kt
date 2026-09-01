package com.rommapp.rommp.di

import com.rommapp.rommp.data.music.CrateMembershipRepository
import com.rommapp.rommp.data.music.DefaultCrateMembershipRepository
import com.rommapp.rommp.data.music.FavoritesRepository
import com.rommapp.rommp.data.music.MusicRepository
import com.rommapp.rommp.data.music.PlaylistsRepository
import com.rommapp.rommp.data.music.RemoteFavoritesRepository
import com.rommapp.rommp.data.music.RemoteMusicRepository
import com.rommapp.rommp.data.music.RemotePlaylistsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: RemoteMusicRepository): MusicRepository

    @Binds
    @Singleton
    abstract fun bindFavoritesRepository(impl: RemoteFavoritesRepository): FavoritesRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistsRepository(impl: RemotePlaylistsRepository): PlaylistsRepository

    @Binds
    @Singleton
    abstract fun bindCrateMembershipRepository(impl: DefaultCrateMembershipRepository): CrateMembershipRepository
}
