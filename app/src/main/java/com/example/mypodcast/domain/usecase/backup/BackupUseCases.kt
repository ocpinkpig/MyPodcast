package com.example.mypodcast.domain.usecase.backup

import com.example.mypodcast.data.backup.BackupSerializer
import com.example.mypodcast.domain.model.backup.BackupSummary
import com.example.mypodcast.domain.model.backup.ImportResult
import com.example.mypodcast.domain.model.backup.LibraryBackup
import com.example.mypodcast.domain.repository.BackupRepository
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class ExportLibraryUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val serializer: BackupSerializer
) {
    suspend operator fun invoke(output: OutputStream) {
        serializer.write(backupRepository.createBackup(), output)
    }
}

data class InspectedBackup(val backup: LibraryBackup, val summary: BackupSummary)

class InspectBackupUseCase @Inject constructor(
    private val backupRepository: BackupRepository,
    private val serializer: BackupSerializer
) {
    /** @throws com.example.mypodcast.domain.model.backup.InvalidBackupException
     *  @throws com.example.mypodcast.domain.model.backup.UnsupportedBackupVersionException */
    suspend operator fun invoke(input: InputStream): InspectedBackup {
        val backup = serializer.read(input)
        return InspectedBackup(backup, backupRepository.summarize(backup))
    }
}

class ImportLibraryUseCase @Inject constructor(
    private val backupRepository: BackupRepository
) {
    suspend operator fun invoke(backup: LibraryBackup): ImportResult =
        backupRepository.import(backup)
}
