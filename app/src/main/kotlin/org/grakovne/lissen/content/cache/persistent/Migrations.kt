package org.grakovne.lissen.content.cache.persistent

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.grakovne.lissen.lib.domain.LibraryType

val MIGRATION_1_2 =
  object : Migration(1, 2) {
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

val MIGRATION_2_3 =
  object : Migration(2, 3) {
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

val MIGRATION_3_4 =
  object : Migration(3, 4) {
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

val MIGRATION_4_5 =
  object : Migration(4, 5) {
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

val MIGRATION_5_6 =
  object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE detailed_books ADD COLUMN year TEXT")
      db.execSQL("ALTER TABLE detailed_books ADD COLUMN abstract TEXT")
      db.execSQL("ALTER TABLE detailed_books ADD COLUMN publisher TEXT")
    }
  }

val MIGRATION_6_7 =
  object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE detailed_books ADD COLUMN subtitle TEXT")
    }
  }

val MIGRATION_7_8 =
  object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE book_series (
            id TEXT NOT NULL PRIMARY KEY,
            bookId TEXT NOT NULL,
            serialNumber INTEGER NOT NULL,
            name TEXT NOT NULL,
            FOREIGN KEY (bookId) REFERENCES detailed_books(id) ON DELETE CASCADE
        )
        """.trimIndent(),
      )

      db.execSQL("CREATE INDEX index_book_series_bookId ON book_series(bookId)")
    }
  }

val MIGRATION_8_9 =
  object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE book_series_new (
            id TEXT NOT NULL PRIMARY KEY,
            bookId TEXT NOT NULL,
            serialNumber TEXT,
            name TEXT NOT NULL,
            FOREIGN KEY (bookId) REFERENCES detailed_books(id) ON DELETE CASCADE
        )
        """.trimIndent(),
      )

      db.execSQL(
        """
        INSERT INTO book_series_new (id, bookId, serialNumber, name)
        SELECT id, bookId, serialNumber, name FROM book_series
        """.trimIndent(),
      )

      db.execSQL("DROP TABLE book_series")
      db.execSQL("ALTER TABLE book_series_new RENAME TO book_series")
      db.execSQL("CREATE INDEX index_book_series_bookId ON book_series(bookId)")
    }
  }

val MIGRATION_9_10 =
  object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("DROP TABLE IF EXISTS book_series")
      db.execSQL("ALTER TABLE detailed_books ADD COLUMN seriesJson TEXT")
    }
  }

val MIGRATION_10_11 =
  object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
      val now = System.currentTimeMillis() / 1000

      database.execSQL(
        """
        CREATE TABLE detailed_books_new (
            id TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            author TEXT,
            duration INTEGER NOT NULL,
            abstract TEXT,
            subtitle TEXT,
            year TEXT,
            libraryId TEXT,
            publisher TEXT,
            seriesJson TEXT,
            createdAt INTEGER NOT NULL
        )
        """.trimIndent(),
      )

      database.execSQL(
        """
        INSERT INTO detailed_books_new (
            id, title, author, duration, abstract, subtitle, year, libraryId, publisher, seriesJson, createdAt
        )
        SELECT 
            id, title, author, duration, abstract, subtitle, year, libraryId, publisher, seriesJson, $now
        FROM detailed_books
        """.trimIndent(),
      )

      database.execSQL("DROP TABLE detailed_books")
      database.execSQL("ALTER TABLE detailed_books_new RENAME TO detailed_books")
    }
  }

val MIGRATION_11_12 =
  object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
      val now = System.currentTimeMillis() / 1000

      database.execSQL(
        """
        CREATE TABLE detailed_books_new (
            id TEXT NOT NULL PRIMARY KEY,
            title TEXT NOT NULL,
            author TEXT,
            duration INTEGER NOT NULL,
            abstract TEXT,
            subtitle TEXT,
            year TEXT,
            libraryId TEXT,
            publisher TEXT,
            seriesJson TEXT,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL
        )
        """.trimIndent(),
      )

      database.execSQL(
        """
        INSERT INTO detailed_books_new (
            id, title, author, duration, abstract, subtitle, year, libraryId, publisher, seriesJson, createdAt, updatedAt
        )
        SELECT 
            id, title, author, duration, abstract, subtitle, year, libraryId, publisher, seriesJson, createdAt, $now
        FROM detailed_books
        """.trimIndent(),
      )

      database.execSQL("DROP TABLE detailed_books")
      database.execSQL("ALTER TABLE detailed_books_new RENAME TO detailed_books")
    }
  }

val MIGRATION_12_13 =
  object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE detailed_books ADD COLUMN narrator TEXT")
    }
  }

val MIGRATION_13_14 =
  object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE detailed_books ADD COLUMN seriesNames TEXT")
    }
  }

val MIGRATION_14_15 =
  object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE detailed_books ADD COLUMN libraryType TEXT")
    }
  }
