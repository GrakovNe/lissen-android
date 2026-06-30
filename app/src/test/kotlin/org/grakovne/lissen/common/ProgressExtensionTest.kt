package org.grakovne.lissen.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ProgressExtensionTest {
  @Test
  fun `snaps low values to zero`() {
    assertEquals(0.0f, 0.03f.snapProgress())
  }

  @Test
  fun `snaps high values to one`() {
    assertEquals(1.0f, 0.97f.snapProgress())
  }

  @Test
  fun `leaves mid-range values untouched`() {
    assertEquals(0.5f, 0.5f.snapProgress())
  }

  @Test
  fun `boundary values are not snapped`() {
    assertEquals(0.04f, 0.04f.snapProgress())
    assertEquals(0.96f, 0.96f.snapProgress())
  }
}
