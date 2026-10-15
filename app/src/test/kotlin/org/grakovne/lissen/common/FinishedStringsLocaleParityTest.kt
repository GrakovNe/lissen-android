package org.grakovne.lissen.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class FinishedStringsLocaleParityTest {
  @Test
  fun `every finished suggestion string has a russian translation with the same placeholders`() {
    val english = readStrings("src/main/res/values/strings.xml")
    val russian = readStrings("src/main/res/values-ru/strings.xml")

    val finishedKeys = english.keys.filter { it.startsWith("playing_finished_") }
    assertTrue(finishedKeys.isNotEmpty(), "no finished suggestion strings found")

    for (key in finishedKeys) {
      assertTrue(russian.containsKey(key), "missing russian translation for $key")
      assertEquals(placeholders(english.getValue(key)), placeholders(russian.getValue(key)), "placeholder mismatch for $key")
    }
  }

  private fun readStrings(path: String): Map<String, String> {
    val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
    val nodes = document.getElementsByTagName("string")

    return (0 until nodes.length)
      .asSequence()
      .map { nodes.item(it) as Element }
      .associate { it.getAttribute("name") to it.textContent }
  }

  private fun placeholders(value: String): List<String> = Regex("""%\d+\$[a-zA-Z]""").findAll(value).map { it.value }.toList()
}
