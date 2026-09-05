package org.grakovne.lissen.ui.acceptance

import android.app.Activity
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.core.app.NotificationManagerCompat
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.session.MediaController
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.grakovne.lissen.playback.MediaRepository
import org.grakovne.lissen.playback.service.PlaybackService
import org.grakovne.lissen.ui.E2E_HOST
import org.grakovne.lissen.ui.E2E_PASSWORD
import org.grakovne.lissen.ui.E2E_USERNAME
import org.grakovne.lissen.ui.TIMEOUT_MS
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

internal val PLAYBACK_TIMEOUT_MS = 120_000L

private var timberPlanted = false

internal fun plantTestLogging() {
  if (!timberPlanted) {
    Timber.plant(Timber.DebugTree())
    timberPlanted = true
  }
}

internal val seriesItemMatcher =
  SemanticsMatcher("hasSeriesItemTag") { node ->
    node.config
      .getOrElseNullable(SemanticsProperties.TestTag) { null }
      ?.startsWith("seriesItem_") == true
  }

internal fun ComposeTestRule.waitPlaying(
  mediaRepository: MediaRepository,
  timeoutMillis: Long = PLAYBACK_TIMEOUT_MS,
) {
  waitUntil(timeoutMillis) { mediaRepository.isPlaying.value }
}

internal fun ComposeTestRule.waitPaused(
  mediaRepository: MediaRepository,
  timeoutMillis: Long = TIMEOUT_MS,
) {
  waitUntil(timeoutMillis) { mediaRepository.isPlaying.value.not() }
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.waitPlaybackReady(mediaRepository: MediaRepository) {
  waitUntilAtLeastOneExists(
    matcher = hasContentDescription("Play") or hasContentDescription("Pause"),
    timeoutMillis = PLAYBACK_TIMEOUT_MS,
  )
  waitUntil(PLAYBACK_TIMEOUT_MS) { mediaRepository.isPlaybackReady.value }
}

internal fun ComposeTestRule.openBookAndAwaitPlayback(
  bookId: String,
  mediaRepository: MediaRepository,
  attempts: Int = 3,
) {
  var lastError: Throwable? = null

  repeat(attempts) { attempt ->
    clickBookInLibrary(bookId)

    try {
      waitUntil(PLAYBACK_TIMEOUT_MS) { mediaRepository.isPlaybackReady.value }
      waitPlaybackReady(mediaRepository)
      return
    } catch (error: Throwable) {
      lastError = error
      System.err.println("openBookAndAwaitPlayback attempt ${attempt + 1} failed: $error")

      if (attempt < attempts - 1) {
        runCatching { onNodeWithTag("playerBackButton").performClick() }

        waitUntil(TIMEOUT_MS) {
          onAllNodes(hasTestTag("libraryScreen")).fetchSemanticsNodes().isNotEmpty()
        }
      }
    }
  }

  throw checkNotNull(lastError)
}

internal fun ComposeTestRule.ensurePlaying(mediaRepository: MediaRepository) {
  if (mediaRepository.isPlaying.value) {
    return
  }

  if (onAllNodes(hasContentDescription("Play")).fetchSemanticsNodes().isNotEmpty()) {
    onNode(hasContentDescription("Play")).performClick()
  }

  waitPlaying(mediaRepository)
}

internal fun ComposeTestRule.ensurePaused(mediaRepository: MediaRepository) {
  if (mediaRepository.isPlaying.value.not()) {
    return
  }

  onNode(hasContentDescription("Pause")).performClick()
  waitPaused(mediaRepository)
}

internal fun releasePlayback(
  mediaRepository: MediaRepository,
  cache: Cache?,
) {
  InstrumentationRegistry.getInstrumentation().runOnMainSync {
    runCatching { mediaRepository.clearPlayingBook() }

    runCatching {
      val field = MediaRepository::class.java.getDeclaredField("mediaController")
      field.isAccessible = true
      (field.get(mediaRepository) as? MediaController)?.release()
    }
  }

  val context = InstrumentationRegistry.getInstrumentation().targetContext
  context.stopService(Intent(context, PlaybackService::class.java))
  sleepReal(500)

  // Hilt recreates the graph per test; without closing the cache the next
  // SimpleCache instance on the same folder throws on creation
  runCatching { (cache as? SimpleCache)?.release() }
  sleepReal(500)
}

internal fun ComposeTestRule.clickNodeById(id: Int) {
  onNode(SemanticsMatcher("node $id") { it.id == id }).performClick()
}

internal fun ComposeTestRule.scrollToBookInLibrary(bookId: String) {
  // the grid keeps its scroll position across navigations, and performScrollToNode only scrolls forward
  runCatching { onNodeWithTag("libraryGrid").performScrollToIndex(0) }
  onNodeWithTag("libraryGrid")
    .performScrollToNode(hasTestTag("bookItem_$bookId"))
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeTestRule.clickBookInLibrary(bookId: String) {
  scrollToBookInLibrary(bookId)
  onNodeWithTag("bookItem_$bookId").performClick()

  waitUntilAtLeastOneExists(
    matcher = hasTestTag("playerScreen"),
    timeoutMillis = TIMEOUT_MS,
  )
}

@OptIn(ExperimentalTestApi::class)
internal fun <A : Activity> ComposeTestRule.restartActivity(activity: A) {
  InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.recreate() }

  waitUntilAtLeastOneExists(
    matcher = hasTestTag("libraryScreen") or hasTestTag("playerScreen"),
    timeoutMillis = TIMEOUT_MS,
  )
}

internal fun MediaRepository.currentChapterId(): String? {
  val book = playingBook.value ?: return null
  val index = currentChapterIndex.value

  return book.chapters
    .getOrNull(index)
    ?.id
}

internal fun ongoingPlaybackNotificationExists(context: Context): Boolean =
  NotificationManagerCompat
    .from(context)
    .activeNotifications
    .orEmpty()
    .any { notification ->
      notification.packageName == context.packageName &&
        notification.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
    }

internal fun setAirplaneMode(enabled: Boolean) {
  val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
  val descriptor =
    uiAutomation.executeShellCommand("cmd connectivity airplane-mode ${if (enabled) "enable" else "disable"}")

  readShellOutput(descriptor)
}

internal fun grantNotificationPermission() {
  val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
  val descriptor =
    InstrumentationRegistry
      .getInstrumentation()
      .uiAutomation
      .executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")

  readShellOutput(descriptor)
}

private fun readShellOutput(descriptor: ParcelFileDescriptor): String =
  ParcelFileDescriptor
    .AutoCloseInputStream(descriptor)
    .use { stream -> stream.readBytes().decodeToString() }

internal fun bringAppToForeground() {
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  val intent =
    context
      .packageManager
      .getLaunchIntentForPackage(context.packageName)!!
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

  context.startActivity(intent)
}

internal class E2EServerClient {
  private val http =
    OkHttpClient
      .Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(60, TimeUnit.SECONDS)
      .build()

  private val jsonType = "application/json; charset=utf-8".toMediaType()

  fun login(): String {
    val body =
      JSONObject()
        .put("username", E2E_USERNAME)
        .put("password", E2E_PASSWORD)
        .toString()
        .toRequestBody(jsonType)

    val request =
      Request
        .Builder()
        .url("$E2E_HOST/login")
        .header("x-return-tokens", "true")
        .post(body)
        .build()

    http
      .execute(request)
      .use { response ->
        val payload = response.body?.string().orEmpty()
        check(response.isSuccessful) { "server login failed: ${response.code} $payload" }

        return JSONObject(payload).getJSONObject("user").getString("token")
      }
  }

  fun libraries(token: String): JSONArray = getJson(token, "api/libraries").getJSONArray("libraries")

  fun libraryItems(
    token: String,
    libraryId: String,
  ): List<BookSummary> {
    val results = getJson(token, "api/libraries/$libraryId/items?limit=100").getJSONArray("results")

    return results
      .iterObjects()
      .map { item ->
        val media = item.getJSONObject("media")
        val metadata = media.optJSONObject("metadata")

        BookSummary(
          id = item.getString("id"),
          title = metadata?.optString("title").orEmpty(),
          numTracks = media.optInt("numTracks"),
        )
      }
  }

  fun librarySeries(
    token: String,
    libraryId: String,
  ): JSONArray = getJson(token, "api/libraries/$libraryId?include=series").optJSONArray("series") ?: JSONArray()

  fun progress(
    token: String,
    libraryItemId: String,
  ): Double? {
    val payload = getJson(token, "api/me/progress/$libraryItemId")
    val data = payload.optJSONObject("data") ?: payload

    return if (data.has("currentTime")) data.getDouble("currentTime") else null
  }

  private fun getJson(
    token: String,
    path: String,
  ): JSONObject {
    val request =
      Request
        .Builder()
        .url("$E2E_HOST/$path")
        .header("Authorization", "Bearer $token")
        .get()
        .build()

    http
      .execute(request)
      .use { response ->
        val payload = response.body?.string().orEmpty()
        check(response.isSuccessful) { "server request $path failed: ${response.code} $payload" }

        return JSONObject(payload)
      }
  }

  private fun OkHttpClient.execute(request: Request) = newCall(request).execute()

  private fun JSONArray.iterObjects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
}

internal data class BookSummary(
  val id: String,
  val title: String,
  val numTracks: Int,
)

internal fun sleepReal(millis: Long) {
  Thread.sleep(millis)
}
