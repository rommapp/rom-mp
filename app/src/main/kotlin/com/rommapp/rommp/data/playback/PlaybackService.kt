package com.rommapp.rommp.data.playback

import android.app.PendingIntent
import android.content.Intent
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
            .apply { sessionActivity?.let(::setSessionActivity) }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

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
