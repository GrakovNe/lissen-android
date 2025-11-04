package org.grakovne.lissen.lib.domain.connection

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import java.util.UUID

@Keep
@JsonClass(generateAdapter = true)
data class ServerRequestHeader(
  val name: String,
  val value: String,
  val id: UUID = UUID.randomUUID(),
) {
  companion object {
    fun empty() = ServerRequestHeader("", "")

    fun ServerRequestHeader.clean(): ServerRequestHeader {
      val name = this.name.clean()
      val value = this.value.clean()

      return this.copy(name = name, value = value)
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
