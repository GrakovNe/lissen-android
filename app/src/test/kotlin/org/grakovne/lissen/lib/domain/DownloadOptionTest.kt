package org.grakovne.lissen.lib.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DownloadOptionTest {
  @Test
  fun `null option produces disabled id`() = assertEquals("disabled", null.makeId())

  @Test
  fun `AllItems produces all_items id`() = assertEquals("all_items", AllItemsDownloadOption.makeId())

  @Test
  fun `CurrentItem produces current_item id`() = assertEquals("current_item", CurrentItemDownloadOption.makeId())

  @Test
  fun `RemainingItems produces remaining_items id`() = assertEquals("remaining_items", RemainingItemsDownloadOption.makeId())

  @Test
  fun `NumberItem encodes count in id`() = assertEquals("number_items_5", NumberItemDownloadOption(5).makeId())

  @Test
  fun `disabled string produces null`() = assertNull("disabled".makeDownloadOption())

  @Test
  fun `null string produces null`() = assertNull(null.makeDownloadOption())

  @Test
  fun `all_items string produces AllItemsDownloadOption`() = assertEquals(AllItemsDownloadOption, "all_items".makeDownloadOption())

  @Test
  fun `current_item string produces CurrentItemDownloadOption`() =
    assertEquals(CurrentItemDownloadOption, "current_item".makeDownloadOption())

  @Test
  fun `remaining_items string produces RemainingItemsDownloadOption`() =
    assertEquals(RemainingItemsDownloadOption, "remaining_items".makeDownloadOption())

  @Test
  fun `number_items_N string produces NumberItemDownloadOption with correct count`() {
    val option = "number_items_7".makeDownloadOption()
    assertInstanceOf(NumberItemDownloadOption::class.java, option)
    assertEquals(7, (option as NumberItemDownloadOption).itemsNumber)
  }

  @Test
  fun `unknown string produces null`() = assertNull("something_else".makeDownloadOption())

  @Test
  fun `singleton options roundtrip through makeId and makeDownloadOption`() {
    listOf(AllItemsDownloadOption, CurrentItemDownloadOption, RemainingItemsDownloadOption).forEach { option ->
      assertEquals(option, option.makeId().makeDownloadOption())
    }
  }

  @Test
  fun `NumberItemDownloadOption roundtrips preserving count`() {
    val roundtripped = NumberItemDownloadOption(3).makeId().makeDownloadOption()
    assertInstanceOf(NumberItemDownloadOption::class.java, roundtripped)
    assertEquals(3, (roundtripped as NumberItemDownloadOption).itemsNumber)
  }
}
