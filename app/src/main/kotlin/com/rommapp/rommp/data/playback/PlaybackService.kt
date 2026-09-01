package com.rommapp.rommp.data.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts [PlaybackManager]'s shared player in a MediaSession so playback keeps
 * running with a media notification and lockscreen/hardware controls when the
 * app leaves the foreground. [PlaybackManager] owns the player and starts this
 * service when playback begins; the service only wraps the player while alive
 * and never releases it.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playbackManager: PlaybackManager

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val sessionActivity = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        mediaSession = MediaSession.Builder(this, playbackManager.acquirePlayer())
            .setCallback(ConnectCallback())
            .apply { sessionActivity?.let(::setSessionActivity) }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * The session is exported for the system's media surfaces, which also lets
     * any installed app connect as a controller. Unrecognized controllers keep
     * transport control but cannot replace the queue, so they can never route
     * the player (and its auth header) at URIs of their choosing.
     */
    private inner class ConnectCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val trusted = controller.uid == Process.myUid() ||
                controller.uid == Process.SYSTEM_UID ||
                session.isMediaNotificationController(controller) ||
                session.isAutoCompanionController(controller) ||
                session.isAutomotiveController(controller)
            if (trusted) {
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            }
            val restricted = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .remove(Player.COMMAND_SET_MEDIA_ITEM)
                .remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(restricted)
                .build()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
