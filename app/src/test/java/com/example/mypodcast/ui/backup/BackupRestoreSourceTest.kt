package com.example.mypodcast.ui.backup

import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.io.File

class BackupRestoreSourceTest {
    private fun source(path: String): String {
        val root = File(System.getProperty("user.dir"))
        return File(root, "src/main/java/com/example/mypodcast/$path").readText()
    }

    @Test
    fun navigationKeys_declareBackupRestoreKey() {
        assertTrue(source("NavigationKeys.kt").contains("data object BackupRestoreNavKey : NavKey"))
    }

    @Test
    fun navigation_registersBackupRestoreEntry() {
        assertTrue(source("Navigation.kt").contains("entry<BackupRestoreNavKey>"))
    }

    @Test
    fun libraryScreen_opensBackupRestoreFromOverflowMenu() {
        val library = source("ui/library/LibraryScreen.kt")
        assertTrue(library.contains("DropdownMenu"))
        assertTrue(library.contains("Backup and restore"))
        assertTrue(library.contains("onOpenBackupRestore"))
    }

    @Test
    fun screen_launchesSafPickersAndConfirmDialog() {
        val screen = source("ui/backup/BackupRestoreScreen.kt")
        assertTrue(screen.contains("CreateDocument(\"application/json\")"))
        assertTrue(screen.contains("OpenDocument"))
        assertTrue(screen.contains("Restore this backup?"))
    }
}
