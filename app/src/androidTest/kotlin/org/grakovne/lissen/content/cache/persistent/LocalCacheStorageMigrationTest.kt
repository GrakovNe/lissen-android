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

    // runMigrationsAndValidate also validates that the resulting schema matches the exported v19 schema.
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

  companion object {
    private const val TEST_DB = "local-cache-migration-test"
  }
}
