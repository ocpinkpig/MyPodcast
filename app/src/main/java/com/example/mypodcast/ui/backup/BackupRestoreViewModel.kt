package com.example.mypodcast.ui.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mypodcast.domain.model.backup.BackupSummary
import com.example.mypodcast.domain.model.backup.FeedTarget
import com.example.mypodcast.domain.model.backup.InvalidBackupException
import com.example.mypodcast.domain.model.backup.LibraryBackup
import com.example.mypodcast.domain.model.backup.UnsupportedBackupVersionException
import com.example.mypodcast.domain.repository.EpisodeRepository
import com.example.mypodcast.domain.usecase.backup.ExportLibraryUseCase
import com.example.mypodcast.domain.usecase.backup.ImportLibraryUseCase
import com.example.mypodcast.domain.usecase.backup.InspectBackupUseCase
import com.example.mypodcast.di.IoDispatcher
import com.example.mypodcast.work.RestoreProgress
import com.example.mypodcast.work.RestoreScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PendingImport(
    val fileName: String,
    val backup: LibraryBackup,
    val summary: BackupSummary
)

data class BackupRestoreUiState(
    val isExporting: Boolean = false,
    val lastExportAt: Long? = null,
    val pendingImport: PendingImport? = null,
    val isImporting: Boolean = false,
    val restoreProgress: RestoreProgress? = null,
    val message: String? = null
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportLibrary: ExportLibraryUseCase,
    private val inspectBackup: InspectBackupUseCase,
    private val importLibrary: ImportLibraryUseCase,
    private val restoreScheduler: RestoreScheduler,
    private val episodeRepository: EpisodeRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        BackupRestoreUiState(
            lastExportAt = prefs.getLong(KEY_LAST_EXPORT, 0L).takeIf { it > 0L }
        )
    )
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            restoreScheduler.observeProgress().collect { progress ->
                _uiState.update { it.copy(restoreProgress = progress) }
            }
        }
    }

    fun export(uri: Uri) {
        if (_uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    // "wt" truncates when overwriting a longer existing document;
                    // some providers (and Robolectric shadows) only accept the default mode.
                    val output = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
                        ?: context.contentResolver.openOutputStream(uri)
                        ?: throw IOException("Couldn't open the selected location")
                    output.use { exportLibrary(it) }
                }
            }.onSuccess {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_EXPORT, now).apply()
                _uiState.update { it.copy(isExporting = false, lastExportAt = now, message = "Backup saved") }
            }.onFailure { error ->
                _uiState.update { it.copy(isExporting = false, message = "Export failed: ${error.message}") }
            }
        }
    }

    fun inspect(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(ioDispatcher) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Couldn't open the selected file")
                    input.use { inspectBackup(it) }
                }
            }.onSuccess { inspected ->
                _uiState.update {
                    it.copy(
                        pendingImport = PendingImport(
                            fileName = displayName(uri),
                            backup = inspected.backup,
                            summary = inspected.summary
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(message = importErrorMessage(error)) }
            }
        }
    }

    fun confirmImport() {
        val pending = _uiState.value.pendingImport ?: return
        if (_uiState.value.isImporting) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            runCatching { importLibrary(pending.backup) }
                .onSuccess { result ->
                    if (result.downloadsToRestore.isNotEmpty()) {
                        restoreScheduler.scheduleRestore(result.downloadsToRestore)
                    }
                    val message = if (result.downloadsToRestore.isEmpty()) "Backup restored"
                    else "Backup restored. Downloading ${result.downloadsToRestore.size} episodes in the background"
                    _uiState.update {
                        it.copy(isImporting = false, pendingImport = null, message = message)
                    }
                    refreshFeeds(result.feedTargets)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isImporting = false, pendingImport = null, message = "Import failed: ${error.message}")
                    }
                }
        }
    }

    fun dismissImport() {
        _uiState.update { it.copy(pendingImport = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun refreshFeeds(targets: List<FeedTarget>) {
        viewModelScope.launch {
            targets.forEach { target ->
                runCatching { episodeRepository.fetchEpisodesForPodcast(target.podcastId, target.feedUrl) }
            }
        }
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) return cursor.getString(index)
                    }
                }
        }
        return uri.lastPathSegment ?: "backup file"
    }

    private fun importErrorMessage(error: Throwable): String = when (error) {
        is UnsupportedBackupVersionException ->
            "This backup was made by a newer version of MyPodcast. Update the app to restore it."
        is InvalidBackupException -> "This file isn't a MyPodcast backup."
        else -> "Couldn't read the backup: ${error.message}"
    }

    private companion object {
        const val KEY_LAST_EXPORT = "last_export_at"
    }
}
