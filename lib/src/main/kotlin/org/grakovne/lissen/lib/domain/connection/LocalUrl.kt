package org.grakovne.lissen.lib.domain.connection

import androidx.annotation.Keep
import com.squareup.moshi.JsonClass
import org.grakovne.lissen.lib.domain.fixUriScheme
import java.net.URI
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
			val name = this.ssid.cleanSsid()
			val value = this.route.cleanUrl()
			
			return this.copy(ssid = name, route = value)
		}
		
		private fun String.cleanSsid(): String {
			val validCharacters = Regex("[\\x20-\\x7E]")
			return this
				.filter { validCharacters.matches(it.toString()) }
				.trim()
		}
		
		private fun String.cleanUrl(): String {
			val validCharacters = Regex("[\\x20-\\x7E]")
			return this
				.filter { validCharacters.matches(it.toString()) }
				.trim()
				.fixUriScheme()
		}
	}
}
