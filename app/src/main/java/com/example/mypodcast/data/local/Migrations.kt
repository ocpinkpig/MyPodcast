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
