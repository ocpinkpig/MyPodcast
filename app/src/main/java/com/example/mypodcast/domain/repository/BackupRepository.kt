package com.example.mypodcast.domain.repository

import com.example.mypodcast.domain.model.backup.BackupSummary
import com.example.mypodcast.domain.model.backup.ImportResult
import com.example.mypodcast.domain.model.backup.LibraryBackup

interface BackupRepository {
    /** Snapshot of all user library state, ready for serialization. */
    suspend fun createBackup(): LibraryBackup

    /** Counts shown in the import confirmation dialog. Skips already-downloaded episodes. */
    suspend fun summarize(backup: LibraryBackup): BackupSummary

    /** Merges the backup into the local database. Never deletes or overwrites fresher local state. */
    suspend fun import(backup: LibraryBackup): ImportResult
}
