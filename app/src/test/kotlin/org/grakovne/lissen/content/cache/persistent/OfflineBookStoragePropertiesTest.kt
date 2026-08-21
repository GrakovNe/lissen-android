package org.grakovne.lissen.content.cache.persistent

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.domain.StoragePath
import org.grakovne.lissen.persistence.preferences.DownloadPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class OfflineBookStoragePropertiesTest {
  private val context = mockk<Context>(relaxed = true)
  private val preferences = mockk<DownloadPreferences>()

  @Test
  fun `configured storage remains active when it is temporarily unavailable`() {
    val configured =
      StoragePath(
        path = "/storage/unavailable/Android/data/org.grakovne.lissen/files/media_cache",
        name = "Unavailable SD card",
      )
    every { preferences.getDownloadStoragePath() } returns configured

    val properties = OfflineBookStorageProperties(context, preferences)

    assertEquals(File(configured.path), properties.provideActiveStorage())
    assertEquals(configured, properties.provideActiveStoragePath())
  }
}
