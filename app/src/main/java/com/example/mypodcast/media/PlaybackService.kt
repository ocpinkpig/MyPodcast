package com.example.mypodcast.media

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.example.mypodcast.MainActivity
import com.example.mypodcast.R
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerController: PlayerController

    private var mediaSession: MediaSession? = null
    private var cachedArtworkUri: Uri? = null
    private var cachedArtwork: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
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
        val rewindCommand = SessionCommand(SESSION_CMD_REWIND_30, android.os.Bundle.EMPTY)
        val forwardCommand = SessionCommand(SESSION_CMD_FORWARD_30, android.os.Bundle.EMPTY)
        val rewindButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_30)
            .setSessionCommand(rewindCommand)
            .setDisplayName("Rewind 30 seconds")
            .build()
        val forwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setSessionCommand(forwardCommand)
            .setDisplayName("Forward 30 seconds")
            .build()
        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val available = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                    .buildUpon()
                    .add(rewindCommand)
                    .add(forwardCommand)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(available)
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: android.os.Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    SESSION_CMD_REWIND_30 -> session.player.seekBack()
                    SESSION_CMD_FORWARD_30 -> session.player.seekForward()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }
        val session = MediaSession.Builder(this, queueAwarePlayer)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(sessionCallback)
            .setCustomLayout(listOf(rewindButton, forwardButton))
            .setMediaButtonPreferences(listOf(rewindButton, forwardButton))
            .build()
        mediaSession = session

        startForegroundWithNotification(session, artwork = null)
        loadArtworkAndUpdate(session)

        session.player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                loadArtworkAndUpdate(session)
            }

            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                loadArtworkAndUpdate(session)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification(session, cachedArtwork)
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REWIND -> mediaSession?.player?.seekBack()
            ACTION_FAST_FORWARD -> mediaSession?.player?.seekForward()
            ACTION_PLAY_PAUSE -> mediaSession?.player?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
            }
            ACTION_NEXT -> mediaSession?.player?.seekToNextMediaItem()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    private fun loadArtworkAndUpdate(session: MediaSession) {
        val uri = session.player.mediaMetadata.artworkUri
        if (uri == null) {
            cachedArtworkUri = null
            cachedArtwork = null
            updateNotification(session, artwork = null)
            return
        }
        if (uri == cachedArtworkUri && cachedArtwork != null) {
            updateNotification(session, cachedArtwork)
            return
        }
        cachedArtworkUri = uri
        Futures.addCallback(
            session.bitmapLoader.loadBitmap(uri),
            object : FutureCallback<Bitmap> {
                override fun onSuccess(result: Bitmap?) {
                    if (uri != cachedArtworkUri) return
                    cachedArtwork = result
                    updateNotification(session, result)
                }

                override fun onFailure(t: Throwable) {
                    // Leave the placeholder; nothing else to do.
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun startForegroundWithNotification(session: MediaSession, artwork: Bitmap?) {
        ServiceCompat.startForeground(
            this,
            PLAYBACK_NOTIFICATION_ID,
            buildNotification(session, artwork),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(session: MediaSession, artwork: Bitmap?) {
        NotificationManagerCompat.from(this)
            .notify(PLAYBACK_NOTIFICATION_ID, buildNotification(session, artwork))
    }

    private fun buildNotification(session: MediaSession, artwork: Bitmap?): android.app.Notification {
        val metadata = session.player.mediaMetadata
        val title = metadata.title?.toString().orEmpty().ifEmpty { getString(R.string.app_name) }
        val text = metadata.artist?.toString().orEmpty()
        val isPlaying = session.player.isPlaying

        val rewindAction = NotificationCompat.Action(
            androidx.media3.session.R.drawable.media3_icon_skip_back_30,
            "Rewind 30s",
            commandPendingIntent(ACTION_REWIND, REQUEST_REWIND)
        )
        val playPauseAction = NotificationCompat.Action(
            if (isPlaying) androidx.media3.session.R.drawable.media3_icon_pause
            else androidx.media3.session.R.drawable.media3_icon_play,
            if (isPlaying) "Pause" else "Play",
            commandPendingIntent(ACTION_PLAY_PAUSE, REQUEST_PLAY_PAUSE)
        )
        val forwardAction = NotificationCompat.Action(
            androidx.media3.session.R.drawable.media3_icon_skip_forward_30,
            "Forward 30s",
            commandPendingIntent(ACTION_FAST_FORWARD, REQUEST_FAST_FORWARD)
        )
        val nextAction = if (playerController.hasQueueItems()) {
            NotificationCompat.Action(
                androidx.media3.session.R.drawable.media3_icon_next,
                "Next",
                commandPendingIntent(ACTION_NEXT, REQUEST_NEXT)
            )
        } else null

        val builder = NotificationCompat.Builder(this, PLAYBACK_CHANNEL_ID)
            .setSmallIcon(androidx.media3.session.R.drawable.media3_icon_play)
            .setContentTitle(title)
            .setContentText(text)
            .setLargeIcon(artwork)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(rewindAction)
            .addAction(playPauseAction)
            .addAction(forwardAction)
        nextAction?.let { builder.addAction(it) }

        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionCompatToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        return builder.build()
    }

    private fun commandPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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

    private companion object {
        const val PLAYBACK_CHANNEL_ID = "playback"
        // Matches DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID.
        const val PLAYBACK_NOTIFICATION_ID = 1001

        const val ACTION_REWIND = "com.example.mypodcast.media.REWIND"
        const val ACTION_FAST_FORWARD = "com.example.mypodcast.media.FAST_FORWARD"
        const val ACTION_PLAY_PAUSE = "com.example.mypodcast.media.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.mypodcast.media.NEXT"

        const val SESSION_CMD_REWIND_30 = "com.example.mypodcast.media.cmd.REWIND_30"
        const val SESSION_CMD_FORWARD_30 = "com.example.mypodcast.media.cmd.FORWARD_30"

        const val REQUEST_REWIND = 1
        const val REQUEST_FAST_FORWARD = 2
        const val REQUEST_PLAY_PAUSE = 3
        const val REQUEST_NEXT = 4
    }
}
