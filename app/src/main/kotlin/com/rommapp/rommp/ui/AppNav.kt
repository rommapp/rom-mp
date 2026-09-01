package com.rommapp.rommp.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rommapp.rommp.ui.album.AlbumScreen
import com.rommapp.rommp.ui.favorites.FavoritesScreen
import com.rommapp.rommp.ui.library.LibraryScreen
import com.rommapp.rommp.ui.player.NowPlayingScreen
import com.rommapp.rommp.ui.playlists.PlaylistDetailScreen
import com.rommapp.rommp.ui.playlists.PlaylistsScreen

object Routes {
    const val LIBRARY = "library"
    const val ALBUM = "album/{romId}/{gameName}"
    const val FAVORITES = "favorites"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST = "playlist/{playlistId}"
    const val NOW_PLAYING = "nowplaying"
    fun album(romId: Int, gameName: String) = "album/$romId/${Uri.encode(gameName)}"
    fun playlist(playlistId: Int) = "playlist/$playlistId"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onGameClick = { game -> nav.navigate(Routes.album(game.romId, game.name)) },
                onOpenFavorites = { nav.navigate(Routes.FAVORITES) },
                onOpenPlaylists = { nav.navigate(Routes.PLAYLISTS) },
                onOpenPlayer = { nav.navigate(Routes.NOW_PLAYING) },
            )
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onBack = { nav.popBackStack() },
                onOpenPlayer = { nav.navigate(Routes.NOW_PLAYING) },
            )
        }
        composable(Routes.PLAYLISTS) {
            PlaylistsScreen(
                onBack = { nav.popBackStack() },
                onOpenPlaylist = { playlist -> nav.navigate(Routes.playlist(playlist.id)) },
                onOpenPlayer = { nav.navigate(Routes.NOW_PLAYING) },
            )
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument("playlistId") { type = NavType.IntType }),
        ) {
            PlaylistDetailScreen(
                onBack = { nav.popBackStack() },
                onOpenPlayer = { nav.navigate(Routes.NOW_PLAYING) },
            )
        }
        composable(
            route = Routes.ALBUM,
            arguments = listOf(
                navArgument("romId") { type = NavType.IntType },
                navArgument("gameName") { type = NavType.StringType },
            ),
        ) {
            AlbumScreen(
                onBack = { nav.popBackStack() },
                onOpenPlayer = { nav.navigate(Routes.NOW_PLAYING) },
            )
        }
        composable(Routes.NOW_PLAYING) {
            NowPlayingScreen(
                onBack = { nav.popBackStack() },
                onReturnToWall = { nav.popBackStack(Routes.LIBRARY, inclusive = false) },
            )
        }
    }
}
