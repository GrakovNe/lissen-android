package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import android.os.Environment
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.grakovne.lissen.domain.StoragePath
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class OfflineBookStoragePropertiesTest {
  @TempDir
  lateinit var externalDir: File

  @TempDir
  lateinit var cacheDir: File

  private val preferences =
    mockk<DownloadPreferences> {
      every { getDownloadStoragePath() } returns null
    }

  private lateinit var context: Context

  private lateinit var properties: OfflineBookStorageProperties

  @BeforeEach
  fun setUp() {
    context =
      mockk {
        every { getExternalFilesDir(OfflineBookStorageProperties.MEDIA_CACHE_FOLDER) } returns externalDir
        every { cacheDir } returns this@OfflineBookStoragePropertiesTest.cacheDir
        every { getSystemService(any<Class<Any>>()) } returns null
      }

    properties = OfflineBookStorageProperties(context, preferences)
  }

  @AfterEach
  fun tearDown() {
    unmockkAll()
  }

  @Nested
  inner class BaseFolderSelection {
    @Test
    fun `uses the external files dir when no storage is configured`() {
      every { preferences.getDownloadStoragePath() } returns null

      assertEquals(externalDir, properties.provideActiveStorage())
    }

    @Test
    fun `prefers the configured writable storage`(
      @TempDir preferred: File,
    ) {
      every { preferences.getDownloadStoragePath() } returns StoragePath(preferred.absolutePath, "SD card")

      assertEquals(preferred, properties.provideActiveStorage())
    }

    @Test
    fun `falls back to the external dir when the configured storage cannot be created`(
      @TempDir parent: File,
    ) {
      val blocked = File(parent, "blocked").apply { writeText("not a directory") }
      every { preferences.getDownloadStoragePath() } returns StoragePath(File(blocked, "storage").absolutePath, "blocked")

      assertEquals(externalDir, properties.provideActiveStorage())
    }

    @Test
    fun `falls back to the cache dir when the external dir cannot be created`() {
      val blocked = File(cacheDir, "blocked").apply { writeText("x") }
      val unreachable = File(blocked, "media_cache")
      every { context.getExternalFilesDir(OfflineBookStorageProperties.MEDIA_CACHE_FOLDER) } returns unreachable

      assertEquals(
        File(cacheDir, OfflineBookStorageProperties.MEDIA_CACHE_FOLDER),
        properties.provideActiveStorage(),
      )
    }
  }

  @Nested
  inner class PathLayout {
    @Test
    fun `book cache is a hashed folder under the base`() {
      val bookCache = properties.provideBookCache("book-1")

      assertEquals(externalDir, bookCache.parentFile)
      assertTrue(bookCache.name.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `media file lives inside the hashed book folder`() {
      val media = properties.provideMediaCachePatch("book-1", "file-1")

      assertEquals(properties.provideBookCache("book-1"), media.parentFile)
      assertTrue(media.name.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `different ids map to different hashes`() {
      assertTrue(properties.provideBookCache("book-1") != properties.provideBookCache("book-2"))
    }

    @Test
    fun `cover path points to cover img inside the book folder`() {
      val cover = properties.provideBookCoverPath("book-1")

      assertEquals("cover.img", cover.name)
      assertEquals(properties.provideBookCache("book-1"), cover.parentFile)
    }

    @Test
    fun `author image lives in the authors folder`() {
      val image = properties.provideAuthorImagePath("Jane Doe")

      assertEquals("authors", image.parentFile?.name)
      assertEquals(externalDir, image.parentFile?.parentFile)
      assertTrue(image.name.endsWith(".img"))
    }
  }

  @Nested
  inner class StoragePaths {
    @Test
    fun `active storage path falls back to the raw path without a storage manager`() {
      val storagePath = properties.provideActiveStoragePath()

      assertEquals(externalDir.absolutePath, storagePath.path)
      assertEquals(externalDir.absolutePath, storagePath.name)
    }

    @Test
    fun `available storages keep only mounted volumes`() {
      mockkStatic(Environment::class)

      val second = File(externalDir.parentFile, "second")
      every { context.getExternalFilesDirs(OfflineBookStorageProperties.MEDIA_CACHE_FOLDER) } returns
        arrayOf(externalDir, second, null)
      every { Environment.getExternalStorageState(externalDir) } returns Environment.MEDIA_MOUNTED
      every { Environment.getExternalStorageState(second) } returns Environment.MEDIA_REMOVED

      val result = properties.provideAvailableStorages()

      assertEquals(1, result.size)
      assertEquals(externalDir.absolutePath, result.single().path)
    }
  }
}
