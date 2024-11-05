package org.grakovne.lissen.content.cache

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE detailed_books RENAME TO detailed_books_old")

        db.execSQL(
            """
            CREATE TABLE detailed_books (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT,
                duration INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO detailed_books (id, title, author, duration)
            SELECT id, title, author, duration FROM detailed_books_old
            """.trimIndent()
        )

        db.execSQL("DROP TABLE detailed_books_old")
    }
}
