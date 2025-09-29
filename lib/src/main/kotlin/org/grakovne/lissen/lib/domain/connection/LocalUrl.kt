package org.grakovne.lissen.lib.domain.connection

import androidx.annotation.Keep
import java.util.UUID

@Keep
data class LocalUrl(
  val ssid: String,
  val route: String,
  val id: UUID = UUID.randomUUID(),
) {
  companion object {
    fun empty() = LocalUrl("", "")

    fun LocalUrl.clean(): LocalUrl {
      val name = this.ssid.clean()
      val value = this.route.clean()

      return this.copy(ssid = name, route = value)
    }

    /**
     * Cleans this string to contain only valid tchar characters for HTTP header names as per RFC 7230.
     *
     * @return A string containing only allowed tchar characters.
     */
    private fun String.clean(): String {
      val invalidCharacters = Regex("[^!#\$%&'*+\\-.^_`|~0-9A-Za-z]")
      return this.replace(invalidCharacters, "").trim()
    }
  }
}
