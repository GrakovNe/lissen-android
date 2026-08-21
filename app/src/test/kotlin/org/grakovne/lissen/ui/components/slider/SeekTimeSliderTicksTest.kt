package org.grakovne.lissen.ui.components.slider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SeekTimeSliderTicksTest {
  @Test
  fun `default range keeps the historical five to sixty ticks`() {
    assertEquals((5..60 step 5).toList(), seekTickIndexes(DEFAULT_MIN_SECONDS, DEFAULT_MAX_SECONDS))
  }

  @Test
  fun `manual rewind and forward rows keep the one to sixty default range`() {
    assertEquals(1, DEFAULT_MIN_SECONDS)
    assertEquals(60, DEFAULT_MAX_SECONDS)
  }

  @Test
  fun `rewind on pause range starts its ticks at ten`() {
    assertEquals((10..60 step 5).toList(), seekTickIndexes(10, 60))
  }

  @Test
  fun `range starting past the maximum yields no ticks`() {
    assertEquals(emptyList<Int>(), seekTickIndexes(61, 60))
  }
}
