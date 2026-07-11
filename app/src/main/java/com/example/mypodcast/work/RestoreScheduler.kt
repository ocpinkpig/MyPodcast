package com.example.mypodcast.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.mypodcast.data.backup.RestoreQueueStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class RestoreProgress(val done: Int, val total: Int)

/** Schedules and observes the background re-download of imported episodes. */
interface RestoreScheduler {
    fun scheduleRestore(episodeGuids: List<String>)

    /** Emits progress while a restore runs, null when idle. */
    fun observeProgress(): Flow<RestoreProgress?>
}

class WorkManagerRestoreScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: RestoreQueueStore
) : RestoreScheduler {

    override fun scheduleRestore(episodeGuids: List<String>) {
        if (episodeGuids.isEmpty()) return
        store.replace(episodeGuids)
        val request = OneTimeWorkRequestBuilder<RestoreDownloadsWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(RestoreDownloadsWorker.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun observeProgress(): Flow<RestoreProgress?> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(RestoreDownloadsWorker.UNIQUE_NAME)
            .map { infos ->
                val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@map null
                val total = running.progress.getInt(RestoreDownloadsWorker.KEY_TOTAL, 0)
                if (total <= 0) null
                else RestoreProgress(
                    done = running.progress.getInt(RestoreDownloadsWorker.KEY_DONE, 0),
                    total = total
                )
            }
}
