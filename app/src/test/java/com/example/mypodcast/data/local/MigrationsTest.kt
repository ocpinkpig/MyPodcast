package com.example.mypodcast.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class MigrationsTest {

    private fun recordingDb(executed: MutableList<String>): SupportSQLiteDatabase =
        Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") executed.add(args!![0] as String)
            null
        } as SupportSQLiteDatabase

    @Test
    fun `migration 7 to 8 adds transcriptStatus with NONE default`() {
        val executed = mutableListOf<String>()

        MIGRATION_7_8.migrate(recordingDb(executed))

        assertEquals(1, executed.size)
        val sql = executed.single()
        assertTrue(sql.contains("ALTER TABLE downloaded_episodes"))
        assertTrue(sql.contains("ADD COLUMN transcriptStatus TEXT NOT NULL DEFAULT 'NONE'"))
    }

    @Test
    fun `migration 8 to 9 adds nullable podcast language column`() {
        val executed = mutableListOf<String>()

        MIGRATION_8_9.migrate(recordingDb(executed))

        assertEquals(1, executed.size)
        val sql = executed.single()
        assertTrue(sql.contains("ALTER TABLE podcasts"))
        assertTrue(sql.contains("ADD COLUMN language TEXT"))
    }
}
