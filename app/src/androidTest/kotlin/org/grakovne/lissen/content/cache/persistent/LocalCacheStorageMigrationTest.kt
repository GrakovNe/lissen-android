package org.grakovne.lissen.content.cache.persistent

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCacheStorageMigrationTest {
  @get:Rule
  val helper =
    MigrationTestHelper(
      InstrumentationRegistry.getInstrumentation(),
      LocalCacheStorage::class.java,
    )

  @Test
  fun migrate18To19_addsNullableSeriesId_andKeepsExistingRows() {
    helper.createDatabase(TEST_DB, 18).use { db ->
      db.execSQL(
        """
        INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt)
        VALUES ('book-1', 'Dune', 0, 0, 0)
        """.trimIndent(),
      )
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, MIGRATION_18_19)

    db.query("SELECT id, seriesId FROM detailed_books WHERE id = 'book-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("book-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
      assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("seriesId")))
    }
  }

  @Test
  fun migrate18To19_allowsStoringSeriesId() {
    helper.createDatabase(TEST_DB, 18).close()

    val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, MIGRATION_18_19)

    db.execSQL(
      """
      INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt, seriesId)
      VALUES ('book-2', 'Dune Messiah', 0, 0, 0, 'ser-dune')
      """.trimIndent(),
    )

    db.query("SELECT seriesId FROM detailed_books WHERE id = 'book-2'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("ser-dune", cursor.getString(cursor.getColumnIndexOrThrow("seriesId")))
    }
  }

  @Test
  fun migrate19To20_addsNullableAuthorsJson_andKeepsExistingRows() {
    helper.createDatabase(TEST_DB, 19).use { db ->
      db.execSQL(
        """
        INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt)
        VALUES ('book-1', 'Dune', 0, 0, 0)
        """.trimIndent(),
      )
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)

    db.query("SELECT id, authorsJson FROM detailed_books WHERE id = 'book-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("book-1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
      assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("authorsJson")))
    }
  }

  @Test
  fun migrate19To20_allowsStoringAuthorsJson() {
    helper.createDatabase(TEST_DB, 19).close()

    val db = helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)

    db.execSQL(
      """
      INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt, authorsJson)
      VALUES ('book-2', 'Dune Messiah', 0, 0, 0, '[{"id":"aut-1","name":"Frank Herbert"}]')
      """.trimIndent(),
    )

    db.query("SELECT authorsJson FROM detailed_books WHERE id = 'book-2'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(
        "[{\"id\":\"aut-1\",\"name\":\"Frank Herbert\"}]",
        cursor.getString(cursor.getColumnIndexOrThrow("authorsJson")),
      )
    }
  }

  @Test
  fun migrate19To20_backfillsAuthorsJsonFromExistingAuthor() {
    helper.createDatabase(TEST_DB, 19).use { db ->
      db.execSQL(
        """
        INSERT INTO detailed_books (id, title, author, duration, createdAt, updatedAt)
        VALUES ('book-1', 'Dune', 'Frank Herbert, Brian Herbert', 0, 0, 0)
        """.trimIndent(),
      )
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)

    db.query("SELECT authorsJson FROM detailed_books WHERE id = 'book-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(
        "[{\"id\":\"\",\"name\":\"Frank Herbert\"},{\"id\":\"\",\"name\":\"Brian Herbert\"}]",
        cursor.getString(cursor.getColumnIndexOrThrow("authorsJson")),
      )
    }
  }

  @Test
  fun migrate14To15_createsCachedBookmarkTable_andKeepsBooks() {
    helper.createDatabase(TEST_DB, 14).use { db ->
      db.execSQL(
        """
        INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt)
        VALUES ('book-1', 'Dune', 0, 0, 0)
        """.trimIndent(),
      )
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15)

    db.query("SELECT id FROM detailed_books WHERE id = 'book-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
    }

    db.execSQL(
      """
      INSERT INTO cached_bookmark (id, title, libraryItemId, createdAt, totalPosition)
      VALUES ('bm-1', 'Chapter 1', 'book-1', 0, 42)
      """.trimIndent(),
    )

    db.query("SELECT totalPosition FROM cached_bookmark WHERE id = 'bm-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(42, cursor.getInt(cursor.getColumnIndexOrThrow("totalPosition")))
    }
  }

  @Test
  fun migrate15To16_addsSyncStateDefaultingToZero() {
    helper.createDatabase(TEST_DB, 15).use { db ->
      db.execSQL(
        """
        INSERT INTO cached_bookmark (id, title, libraryItemId, createdAt, totalPosition)
        VALUES ('bm-1', 'Chapter 1', 'book-1', 0, 42)
        """.trimIndent(),
      )
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16)

    db.query("SELECT syncState FROM cached_bookmark WHERE id = 'bm-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("syncState")))
    }
  }

  @Test
  fun migrate16To17_dropsOrphanMediaProgress_andKeepsLinkedOne() {
    helper.createDatabase(TEST_DB, 16).use { db ->
      db.execSQL(
        """
        INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt)
        VALUES ('book-1', 'Dune', 0, 0, 0)
        """.trimIndent(),
      )
      db.execSQL(
        """
        INSERT INTO media_progress (bookId, currentTime, isFinished, lastUpdate)
        VALUES ('book-1', 12.0, 0, 0), ('ghost', 5.0, 0, 0)
        """.trimIndent(),
      )

      MIGRATION_16_17.migrate(db)

      val survivors = mutableListOf<String>()
      db.query("SELECT bookId FROM media_progress ORDER BY bookId").use { cursor ->
        while (cursor.moveToNext()) {
          survivors += cursor.getString(0)
        }
      }
      assertEquals(listOf("book-1"), survivors)
    }
  }

  @Test
  fun migrateFrom14To19_appliesEveryMigrationInChain() {
    helper.createDatabase(TEST_DB, 14).use { db ->
      db.execSQL(
        """
        INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt)
        VALUES ('book-1', 'Dune', 0, 0, 0)
        """.trimIndent(),
      )
    }

    val db =
      helper.runMigrationsAndValidate(
        TEST_DB,
        20,
        true,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
      )

    db.query("SELECT seriesId FROM detailed_books WHERE id = 'book-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("seriesId")))
    }
  }

  @Test
  fun migrate21To22_collapsesDuplicateRows_andBackfillsPositions() {
    helper.createDatabase(TEST_DB, 21).use { db ->
      db.execSQL(
        """
        INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt)
        VALUES ('book-1', 'Dune', 0, 0, 0)
        """.trimIndent(),
      )
      // two re-caches of the same item in list order; the second marks c1 as downloaded
      db.execSQL(
        """
        INSERT INTO book_chapters (bookChapterId, duration, start, end, title, bookId, isCached)
        VALUES
          ('c1', 10.0, 0.0, 10.0, 'One', 'book-1', 0),
          ('c2', 10.0, 10.0, 20.0, 'Two', 'book-1', 0),
          ('c1', 10.0, 0.0, 10.0, 'One', 'book-1', 1),
          ('c2', 10.0, 10.0, 20.0, 'Two', 'book-1', 0)
        """.trimIndent(),
      )
      db.execSQL(
        """
        INSERT INTO book_files (bookFileId, name, size, duration, mimeType, bookId)
        VALUES
          ('f1', 'one', 0, 10.0, 'audio/mpeg', 'book-1'),
          ('f2', 'two', 0, 10.0, 'audio/mpeg', 'book-1'),
          ('f1', 'one', 0, 10.0, 'audio/mpeg', 'book-1'),
          ('f2', 'two', 0, 10.0, 'audio/mpeg', 'book-1')
        """.trimIndent(),
      )
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 22, true, MIGRATION_21_22)

    val chapters = mutableListOf<Triple<String, Int, Int>>()
    db.query("SELECT bookChapterId, chapterIndex, isCached FROM book_chapters ORDER BY chapterIndex").use { cursor ->
      while (cursor.moveToNext()) {
        chapters += Triple(cursor.getString(0), cursor.getInt(1), cursor.getInt(2))
      }
    }
    assertEquals(listOf(Triple("c1", 0, 1), Triple("c2", 1, 0)), chapters)

    val files = mutableListOf<Pair<String, Int>>()
    db.query("SELECT bookFileId, fileIndex FROM book_files ORDER BY fileIndex").use { cursor ->
      while (cursor.moveToNext()) {
        files += cursor.getString(0) to cursor.getInt(1)
      }
    }
    assertEquals(listOf("f1" to 0, "f2" to 1), files)

    db.query("SELECT publishedAt, season, episode, fileName FROM book_chapters WHERE bookChapterId = 'c1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertTrue(cursor.isNull(0))
      assertTrue(cursor.isNull(1))
      assertTrue(cursor.isNull(2))
      assertTrue(cursor.isNull(3))
    }
  }

  @Test
  fun migrate21To22_backfillsPositionsPerBook() {
    helper.createDatabase(TEST_DB, 21).use { db ->
      db.execSQL(
        """
        INSERT INTO detailed_books (id, title, duration, createdAt, updatedAt)
        VALUES ('book-1', 'One', 0, 0, 0), ('book-2', 'Two', 0, 0, 0)
        """.trimIndent(),
      )
      // rows of the two books interleaved by rowid
      db.execSQL(
        """
        INSERT INTO book_chapters (bookChapterId, duration, start, end, title, bookId, isCached)
        VALUES
          ('a1', 10.0, 0.0, 10.0, 'A1', 'book-1', 0),
          ('b1', 10.0, 0.0, 10.0, 'B1', 'book-2', 0),
          ('a2', 10.0, 10.0, 20.0, 'A2', 'book-1', 0),
          ('b2', 10.0, 10.0, 20.0, 'B2', 'book-2', 0)
        """.trimIndent(),
      )
      db.execSQL(
        """
        INSERT INTO book_files (bookFileId, name, size, duration, mimeType, bookId)
        VALUES
          ('fa1', 'a1', 0, 10.0, 'audio/mpeg', 'book-1'),
          ('fb1', 'b1', 0, 10.0, 'audio/mpeg', 'book-2'),
          ('fa2', 'a2', 0, 10.0, 'audio/mpeg', 'book-1'),
          ('fb2', 'b2', 0, 10.0, 'audio/mpeg', 'book-2')
        """.trimIndent(),
      )
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 22, true, MIGRATION_21_22)

    val chapters = mutableListOf<Pair<String, Int>>()
    db.query("SELECT bookChapterId, chapterIndex FROM book_chapters ORDER BY bookId, chapterIndex").use { cursor ->
      while (cursor.moveToNext()) {
        chapters += cursor.getString(0) to cursor.getInt(1)
      }
    }
    assertEquals(listOf("a1" to 0, "a2" to 1, "b1" to 0, "b2" to 1), chapters)

    val files = mutableListOf<Pair<String, Int>>()
    db.query("SELECT bookFileId, fileIndex FROM book_files ORDER BY bookId, fileIndex").use { cursor ->
      while (cursor.moveToNext()) {
        files += cursor.getString(0) to cursor.getInt(1)
      }
    }
    assertEquals(listOf("fa1" to 0, "fa2" to 1, "fb1" to 0, "fb2" to 1), files)
  }

  @Test
  fun migrate22To23_createsOfflineSessionTable() {
    helper.createDatabase(TEST_DB, 22).close()

    val db = helper.runMigrationsAndValidate(TEST_DB, 23, true, MIGRATION_22_23)

    db.execSQL(
      """
      INSERT INTO offline_playback_session (
        id, libraryItemId, episodeId, libraryType,
        displayTitle, displayAuthor, duration, startTime, currentTime, timeListening,
        startedAt, updatedAt
      )
      VALUES (
        's1', 'book-1', NULL, 'LIBRARY',
        'Dune', NULL, 300.0, 10.0, 55.0, 45.0, 1000, 46000
      )
      """.trimIndent(),
    )

    db
      .query(
        """
        SELECT libraryItemId, timeListening
        FROM offline_playback_session WHERE id = 's1'
        """.trimIndent(),
      ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("book-1", cursor.getString(cursor.getColumnIndexOrThrow("libraryItemId")))
        assertEquals(45.0, cursor.getDouble(cursor.getColumnIndexOrThrow("timeListening")), 0.0)
      }
  }

  @Test
  fun migrate23To24_rewritesUnknownLibraryTypeAsLibrary() {
    helper.createDatabase(TEST_DB, 23).use { db ->
      db.execSQL("INSERT INTO libraries (id, title, type) VALUES ('lib-1', 'Mixed', 'UNKNOWN')")
      db.execSQL("INSERT INTO libraries (id, title, type) VALUES ('lib-2', 'Shows', 'PODCAST')")
      db.execSQL(
        """
        INSERT INTO offline_playback_session (
          id, libraryItemId, episodeId, libraryType,
          displayTitle, displayAuthor, duration, startTime, currentTime, timeListening,
          startedAt, updatedAt
        )
        VALUES ('s1', 'book-1', NULL, 'UNKNOWN', 'Dune', NULL, 300.0, 10.0, 55.0, 45.0, 1000, 46000)
        """.trimIndent(),
      )
    }

    val db = helper.runMigrationsAndValidate(TEST_DB, 24, true, MIGRATION_23_24)

    db.query("SELECT id, type FROM libraries ORDER BY id").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("LIBRARY", cursor.getString(cursor.getColumnIndexOrThrow("type")))
      assertTrue(cursor.moveToNext())
      assertEquals("PODCAST", cursor.getString(cursor.getColumnIndexOrThrow("type")))
    }
    db.query("SELECT libraryType FROM offline_playback_session WHERE id = 's1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("LIBRARY", cursor.getString(cursor.getColumnIndexOrThrow("libraryType")))
    }
  }

  companion object {
    private const val TEST_DB = "local-cache-migration-test"
  }
}
