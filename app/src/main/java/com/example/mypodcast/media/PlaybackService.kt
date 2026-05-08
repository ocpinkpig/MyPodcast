package com.example.mypodcast.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.mypodcast.MainActivity
import com.example.mypodcast.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerController: PlayerController

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val queueAwarePlayer = QueueAwarePlayer(
            wrapped = playerController.exoPlayer,
            hasQueueItems = { playerController.hasQueueItems() },
            onNext = { playerController.playNextInQueue() }
        )
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val session = MediaSession.Builder(this, queueAwarePlayer)
            .setSessionActivity(sessionActivityIntent)
            .build()
        mediaSession = session
        startAsForegroundMediaService(session)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun startAsForegroundMediaService(session: MediaSession) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(PLAYBACK_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        PLAYBACK_CHANNEL_ID,
                        "Playback",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val notification = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("MyPodcast")
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionCompatToken)
            )
            .build()
        ServiceCompat.startForeground(
            this,
            PLAYBACK_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private companion object {
        const val PLAYBACK_CHANNEL_ID = "playback"
        // Matches DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID so
        // Media3's notification, when produced, replaces this placeholder.
        const val PLAYBACK_NOTIFICATION_ID = 1001
    }
}
