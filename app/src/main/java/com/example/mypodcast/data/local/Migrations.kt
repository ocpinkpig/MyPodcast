package com.example.mypodcast.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN isPlayed INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
    }
}


internal val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS queue_items (
                episodeGuid TEXT NOT NULL PRIMARY KEY,
                position INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_queue_items_position ON queue_items(position)")
    }
}

internal val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
    }
}

internal val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE episodes ADD COLUMN transcriptUrl TEXT")
        db.execSQL("ALTER TABLE episodes ADD COLUMN transcriptType TEXT")
    }
}

internal val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS saved_moments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                episodeGuid TEXT NOT NULL,
                positionMs INTEGER NOT NULL,
                clipStartMs INTEGER NOT NULL,
                clipEndMs INTEGER NOT NULL,
                transcriptText TEXT,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(episodeGuid) REFERENCES episodes(guid) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_moments_episodeGuid ON saved_moments(episodeGuid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_moments_createdAt ON saved_moments(createdAt)")
    }
}

internal val MIGRATION_7_8: Migration = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE downloaded_episodes ADD COLUMN transcriptStatus TEXT NOT NULL DEFAULT 'NONE'"
        )
    }
}
