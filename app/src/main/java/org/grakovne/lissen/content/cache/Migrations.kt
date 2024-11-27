package org.grakovne.lissen.content.cache

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.grakovne.lissen.channel.common.LibraryType

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
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO detailed_books (id, title, author, duration)
            SELECT id, title, author, duration FROM detailed_books_old
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE detailed_books_old")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE libraries ADD COLUMN type TEXT")

        db.execSQL(
            """
            UPDATE libraries
            SET type = '${LibraryType.LIBRARY.name}'
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE libraries_new (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                type TEXT NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO libraries_new (id, title, type)
            SELECT id, title, type FROM libraries
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE libraries")
        db.execSQL("ALTER TABLE libraries_new RENAME TO libraries")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE detailed_books ADD COLUMN libraryId TEXT")

        db.execSQL(
            """
            UPDATE detailed_books
            SET libraryId = NULL
            """.trimIndent(),
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE book_chapters ADD COLUMN isCached INTEGER NOT NULL DEFAULT 0")

        db.execSQL(
            """
            UPDATE book_chapters
            SET isCached = 1
            """.trimIndent(),
        )
    }
}
