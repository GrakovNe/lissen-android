package org.grakovne.lissen.content

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockResponseBody
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.BufferedSink
import org.grakovne.lissen.channel.audiobookshelf.common.api.AudioBookshelfRepository
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.channel.common.createOkHttpClient
import org.grakovne.lissen.persistence.preferences.ConnectionPreferences
import org.grakovne.lissen.persistence.preferences.SessionPreferences
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Research reproduction for the ACRA report a78bf35f:
 * java.lang.OutOfMemoryError on the OkHttp HTTP/2 reader thread
 * (Http2Stream$FramingSource.receive -> okio Buffer.write -> Segment.<init>).
 *
 * The reader thread copies DATA frames into a per-stream receive buffer of
 * OkHttp's client window size (16 MiB). The app's fetchBookCover()/fetchAuthorCover()
 * path reads the WHOLE response body into an in-memory okio Buffer with no size
 * limit (AudioBookshelfRepository.fetchBookCover -> Buffer().writeAll(source)).
 * A server that returns a huge file on /api/items/{id}/cover?raw=1 therefore
 * fills the Java heap while the reader thread keeps feeding it -> OOM.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CoverOomReproTest {
  @get:Rule(order = 0)
  val hiltRule = HiltAndroidRule(this)

  @Inject
  lateinit var session: SessionPreferences

  @Inject
  lateinit var connection: ConnectionPreferences

  @Inject
  lateinit var repository: AudioBookshelfRepository

  private lateinit var server: MockWebServer
  private var previousHandler: Thread.UncaughtExceptionHandler? = null

  @Volatile
  private var readerThreadDied: Throwable? = null

  private val bodyBytes =
    java.util.concurrent.atomic
      .AtomicLong(MAX_BODY_BYTES)

  private fun streamingBody(maxBytes: Long): MockResponseBody =
    object : MockResponseBody {
      override val contentLength: Long = -1L

      override fun writeTo(sink: BufferedSink) {
        val chunk = ByteArray(64 * 1024)
        var written = 0L
        while (written < maxBytes) {
          sink.write(chunk)
          sink.flush()
          written += chunk.size
        }
      }
    }

  @Before
  fun setUp() {
    hiltRule.inject()

    previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
      if (error is OutOfMemoryError && thread.name.contains("OkHttp")) {
        readerThreadDied = error
        Log.e(TAG, "reader thread ${thread.name} died of OOM (user's crash signature)", error)
      } else {
        previousHandler?.uncaughtException(thread, error)
      }
    }

    val cert =
      HeldCertificate
        .Builder()
        .addSubjectAlternativeName("127.0.0.1")
        .duration(365, TimeUnit.DAYS)
        .build()
    val handshake = HandshakeCertificates.Builder().heldCertificate(cert).build()

    server = MockWebServer()
    server.useHttps(handshake.sslSocketFactory())
    server.protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1)
    server.dispatcher =
      object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
          MockResponse
            .Builder()
            .addHeader("Content-Type", "image/jpeg")
            .body(streamingBody(bodyBytes.get()))
            .build()
      }
    server.start()

    session.saveHost("https://127.0.0.1:${server.port}")
    session.saveToken("fake-token")
    connection.saveSslBypass(true)
  }

  @After
  fun tearDown() {
    runCatching { server.close() }
    previousHandler?.let { Thread.setDefaultUncaughtExceptionHandler(it) }
  }

  /**
   * Regression for ACRA a78bf35f: an unbounded body on
   * /api/items/{id}/cover?raw=1 used to be buffered entirely into the Java
   * heap (Buffer().writeAll) and OOM-killed the process with the OkHttp
   * HTTP/2 reader thread on the stack. The body is now streamed to disk, so
   * even a 2 GB stream must not touch the heap beyond a chunk.
   */
  @Test
  fun hugeRawCoverStreamsToDiskWithoutHeapGrowth() {
    val runtime = Runtime.getRuntime()
    System.gc()
    Thread.sleep(200)
    val baseline = runtime.totalMemory() - runtime.freeMemory()

    val result = runBlocking { repository.fetchBookCover("book1", null) }

    val growthMb = ((runtime.totalMemory() - runtime.freeMemory()) - baseline) / (1024 * 1024)

    val file = (result as? OperationResult.Success)?.data
    val size = file?.length() ?: -1L
    Log.e(
      TAG,
      "result=${result::class.simpleName} fileSize=${size / (1024 * 1024)}MB growth=${growthMb}MB readerDied=${readerThreadDied != null}",
    )

    assertTrue("2GB body must stream to disk, got $result", result is OperationResult.Success)
    assertTrue("file must hold the full body, got $size bytes", size == MAX_BODY_BYTES)
    assertTrue("heap must stay bounded, grew ${growthMb}MB", growthMb < 64)
    assertTrue("okhttp reader must survive", readerThreadDied == null)

    file?.delete()
  }

  /**
   * A legitimately large cover (30 MB) must succeed and must never be held
   * in the Java heap: it is streamed straight to a temp file.
   */
  @Test
  fun largeCoverStreamsToDiskWithoutHeapGrowth() {
    bodyBytes.set(LARGE_COVER_BYTES)

    val runtime = Runtime.getRuntime()
    System.gc()
    Thread.sleep(200)
    val baseline = runtime.totalMemory() - runtime.freeMemory()

    val result = runBlocking { repository.fetchBookCover("book1", null) }

    val growthMb = ((runtime.totalMemory() - runtime.freeMemory()) - baseline) / (1024 * 1024)

    val file = (result as? OperationResult.Success)?.data
    val size = file?.length() ?: -1L
    Log.e(TAG, "result=${result::class.simpleName} fileSize=${size / (1024 * 1024)}MB growth=${growthMb}MB")

    assertTrue("30MB cover must succeed, got $result", result is OperationResult.Success)
    assertTrue("file must hold the full body, got $size bytes", size == LARGE_COVER_BYTES)
    assertTrue("heap must stay bounded, grew ${growthMb}MB", growthMb < 32)

    file?.delete()
  }

  /**
   * Quantifies the per-stream cost of an opened-but-never-consumed HTTP/2
   * response on ART: OkHttp buffers up to the 16 MiB client window per stream
   * on the reader thread. ~30 leaked streams are enough to exhaust a 512 MB
   * largeHeap budget on a Pixel-class device.
   */
  @Test
  fun leakedStreamsCostClientWindowEach() {
    val client =
      createOkHttpClient(
        requestHeaders = null,
        session = session,
        connection = connection,
        context =
          androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .targetContext,
      )

    val runtime = Runtime.getRuntime()
    System.gc()
    Thread.sleep(300)
    val baseline = runtime.totalMemory() - runtime.freeMemory()

    val responses = mutableListOf<Response>()
    try {
      repeat(STREAMS) { i ->
        val request = Request.Builder().url("https://127.0.0.1:${server.port}/cover/$i").build()
        responses += client.newCall(request).execute()
      }
      Thread.sleep(4000)
      val leaked = runtime.totalMemory() - runtime.freeMemory() - baseline
      val perStreamMb = leaked / STREAMS / (1024 * 1024)
      val leakedMb = leaked / (1024 * 1024)
      Log.e(TAG, "leaked $STREAMS unconsumed h2 streams -> $leakedMb MB (~$perStreamMb MB/stream)")
      assertTrue(
        "expected ~16 MB per leaked stream, got $perStreamMb MB/stream",
        leaked > STREAMS.toLong() * 8L * 1024L * 1024L,
      )
    } finally {
      responses.forEach { runCatching { it.close() } }
    }
  }

  companion object {
    private const val TAG = "CoverOomRepro"
    private const val MAX_BODY_BYTES = 2L * 1024 * 1024 * 1024
    private const val LARGE_COVER_BYTES = 30L * 1024 * 1024
    private const val STREAMS = 8
  }
}
