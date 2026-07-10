package com.example.mypodcast.data.backup

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RestoreQueue(val total: Int, val pending: List<String>)

/**
 * Pending episode GUIDs awaiting re-download after a backup import.
 * Persisted as JSON in filesDir so the restore survives process death and
 * WorkManager retries, and isn't limited by the 10 KB Work Data cap.
 */
@Singleton
class RestoreQueueStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val file: File
        get() = File(context.filesDir, "restore_downloads.json")

    @Synchronized
    fun replace(guids: List<String>) {
        write(RestoreQueue(total = guids.size, pending = guids))
    }

    @Synchronized
    fun snapshot(): RestoreQueue = read()

    @Synchronized
    fun remove(guid: String) {
        val queue = read()
        write(queue.copy(pending = queue.pending - guid))
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun read(): RestoreQueue {
        if (!file.exists()) return EMPTY
        return runCatching { gson.fromJson(file.readText(), RestoreQueue::class.java) }
            .getOrNull() ?: EMPTY
    }

    private fun write(queue: RestoreQueue) {
        file.writeText(gson.toJson(queue))
    }

    private companion object {
        val EMPTY = RestoreQueue(total = 0, pending = emptyList())
    }
}
