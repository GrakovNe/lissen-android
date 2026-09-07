package org.grakovne.lissen.domain.connection

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

    private fun String.clean(): String {
      val invalidCharacters = Regex("[^!#\$%&'*+\\-.^_`|~0-9A-Za-z]")
      return this.replace(invalidCharacters, "").trim()
    }
  }
}
