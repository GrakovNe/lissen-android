package org.grakovne.lissen.lib.domain.connection

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import java.util.UUID

@Keep
@JsonClass(generateAdapter = true)
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
    
    private fun String.clean(): String {
      val validCharacters = Regex("[\\x20-\\x7E]")
      return this
        .filter { validCharacters.matches(it.toString()) }
        .trim()
    }
  }
}
