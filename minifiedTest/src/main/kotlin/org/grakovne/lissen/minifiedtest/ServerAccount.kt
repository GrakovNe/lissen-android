package org.grakovne.lissen.minifiedtest

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class ServerBook(
  val id: String,
  val title: String,
)

data class ServerProgress(
  val isFinished: Boolean,
  val currentTime: Double,
  val lastUpdate: Long,
)

class ServerAccount private constructor(
  private val host: String,
  private val token: String,
  private val defaultLibraryId: String,
) {
  /** Every other test opens the first book of the grid, which sorts by title ascending, on parallel shards of the same account. */
  fun lastBookByTitle(): ServerBook {
    val item =
      JSONObject(get("/api/libraries/$defaultLibraryId/items?sort=media.metadata.title&desc=1&limit=1"))
        .getJSONArray("results")
        .getJSONObject(0)
    return ServerBook(id = item.getString("id"), title = item.getJSONObject("media").getJSONObject("metadata").getString("title"))
  }

  fun progress(itemId: String): ServerProgress {
    val json = JSONObject(get("/api/me/progress/$itemId"))
    return ServerProgress(
      isFinished = json.getBoolean("isFinished"),
      currentTime = json.getDouble("currentTime"),
      lastUpdate = json.getLong("lastUpdate"),
    )
  }

  /** The server unfinishes a progress whose position changes in the same request as the mark. */
  fun finishAt(
    itemId: String,
    currentTime: Double,
  ) {
    patch("/api/me/progress/$itemId", """{"currentTime":$currentTime}""")
    patch("/api/me/progress/$itemId", """{"isFinished":true}""")
  }

  private fun request(path: String) = Request.Builder().url(host + path).header("Authorization", "Bearer $token")

  private fun get(path: String): String = request(path).build().send()

  private fun patch(
    path: String,
    body: String,
  ) {
    request(path).patch(body.toRequestBody(JSON)).build().send()
  }

  companion object {
    private val JSON = "application/json".toMediaType()

    private val client = OkHttpClient()

    private fun Request.send(): String =
      client.newCall(this).execute().use { response ->
        val body = response.body.string()
        if (!response.isSuccessful) throw AssertionError("$method $url failed with ${response.code}: $body")
        body
      }

    fun login(): ServerAccount {
      val body = JSONObject().put("username", E2E_USERNAME).put("password", E2E_PASSWORD).toString()
      val json = JSONObject(Request.Builder().url("$E2E_HOST/login").post(body.toRequestBody(JSON)).build().send())
      return ServerAccount(
        host = E2E_HOST,
        token = json.getJSONObject("user").getString("accessToken"),
        defaultLibraryId = json.getString("userDefaultLibraryId"),
      )
    }
  }
}
