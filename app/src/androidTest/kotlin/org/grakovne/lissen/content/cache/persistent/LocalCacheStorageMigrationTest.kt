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

    // The new table is present and usable.
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
        19,
        true,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
      )

    db.query("SELECT seriesId FROM detailed_books WHERE id = 'book-1'").use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("seriesId")))
    }
  }

  companion object {
    private const val TEST_DB = "local-cache-migration-test"
  }
}
