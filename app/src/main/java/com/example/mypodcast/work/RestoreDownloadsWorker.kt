package com.example.mypodcast.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.mypodcast.data.backup.RestoreQueueStore
import com.example.mypodcast.data.download.EpisodeAudioDownloader
import com.example.mypodcast.data.local.dao.DownloadedEpisodeDao
import com.example.mypodcast.data.local.dao.EpisodeDao
import com.example.mypodcast.data.local.entity.DownloadedEpisodeEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Re-downloads episode audio after a backup import. Reads pending GUIDs from
 * [RestoreQueueStore]; each completed file gets its downloaded_episodes row,
 * so the Library fills in progressively and retries are idempotent.
 */
@HiltWorker
class RestoreDownloadsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val store: RestoreQueueStore,
    private val episodeDao: EpisodeDao,
    private val downloadedEpisodeDao: DownloadedEpisodeDao,
    private val downloader: EpisodeAudioDownloader
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val queue = store.snapshot()
        if (queue.pending.isEmpty()) {
            store.clear()
            return Result.success()
        }

        var anyFailed = false
        for (guid in queue.pending) {
            val done = queue.total - store.snapshot().pending.size
            setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to queue.total))
            // Foreground promotion can be refused (e.g. background start
            // restrictions); the work still proceeds without a notification.
            runCatching { setForeground(foregroundInfo(done, queue.total)) }

            if (downloadedEpisodeDao.getByGuid(guid) != null) {
                store.remove(guid)
                continue
            }
            val episode = episodeDao.getByGuid(guid)
            if (episode == null) {
                store.remove(guid)
                continue
            }
            try {
                val file = downloader.download(
                    episodeGuid = guid,
                    audioUrl = episode.audioUrl,
                    expectedFileSizeBytes = episode.fileSizeBytes
                )
                downloadedEpisodeDao.insert(
                    DownloadedEpisodeEntity(
                        episodeGuid = guid,
                        podcastId = episode.podcastId,
                        localFilePath = file.absolutePath,
                        fileSizeBytes = file.length()
                    )
                )
                store.remove(guid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                anyFailed = true
            }
        }

        return when {
            !anyFailed -> {
                store.clear()
                Result.success()
            }
            runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> {
                store.clear()
                Result.failure()
            }
        }
    }

    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Restore downloads", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Restoring downloads")
            .setContentText("${done + 1} of $total")
            .setProgress(total, done, false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val UNIQUE_NAME = "restore-downloads"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val CHANNEL_ID = "restore_downloads"
        const val NOTIFICATION_ID = 2001
        const val MAX_RETRIES = 5
    }
}
