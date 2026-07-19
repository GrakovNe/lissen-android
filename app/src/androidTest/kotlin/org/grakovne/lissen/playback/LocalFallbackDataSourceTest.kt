package org.grakovne.lissen.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import org.grakovne.lissen.channel.common.OperationResult
import org.grakovne.lissen.content.LissenMediaProvider
import org.grakovne.lissen.playback.service.LocalFallbackDataSource
import org.grakovne.lissen.playback.service.toLissenUri
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

@UnstableApi
@RunWith(AndroidJUnit4::class)
class LocalFallbackDataSourceTest {
  private val mediaProvider = mockk<LissenMediaProvider>()

  private val payload = ByteArray(1000) { (it % 251).toByte() }
  private val lissenUri = toLissenUri("book-1", "file-1")
  private val remoteUri = Uri.parse("https://server:8080/api/items/book-1/file/file-1")

  @Test
  fun resumes_from_local_file_when_network_read_fails_after_download() {
    val localFile = writeTempFile(payload)
    val localUri = Uri.fromFile(localFile)

    // open() resolves to the remote stream (not yet downloaded), the recovery resolves to the file.
    every { mediaProvider.provideFileUri("book-1", "file-1") } returnsMany
      listOf(
        OperationResult.Success(remoteUri),
        OperationResult.Success(localUri),
      )

    val serveBeforeFailure = 400
    val dataSource =
      LocalFallbackDataSource(
        upstream = FakeNetworkDataSource(payload, serveBeforeFailure),
        local = FileDataSource(),
        mediaProvider = mediaProvider,
      )

    val read = drainFully(dataSource)

    // The whole chapter is delivered without ExoPlayer ever seeing the network error.
    assertArrayEquals(payload, read)
  }

  @Test
  fun resumes_with_correct_byte_alignment_when_starting_mid_file() {
    val localFile = writeTempFile(payload)
    val localUri = Uri.fromFile(localFile)

    every { mediaProvider.provideFileUri("book-1", "file-1") } returnsMany
      listOf(
        OperationResult.Success(remoteUri),
        OperationResult.Success(localUri),
      )

    val startPosition = 100L
    val serveBeforeFailure = 200
    val dataSource =
      LocalFallbackDataSource(
        upstream = FakeNetworkDataSource(payload, serveBeforeFailure),
        local = FileDataSource(),
        mediaProvider = mediaProvider,
      )

    val read = drainFully(dataSource, position = startPosition)

    assertArrayEquals(payload.copyOfRange(startPosition.toInt(), payload.size), read)
  }

  @Test
  fun rethrows_network_error_when_local_file_is_not_available() {
    // The file is still not downloaded, so the recovery also resolves to the remote stream.
    every { mediaProvider.provideFileUri("book-1", "file-1") } returns
      OperationResult.Success(remoteUri)

    val dataSource =
      LocalFallbackDataSource(
        upstream = FakeNetworkDataSource(payload, serveBeforeFailure = 100),
        local = FileDataSource(),
        mediaProvider = mediaProvider,
      )

    dataSource.open(DataSpec.Builder().setUri(lissenUri).build())

    assertThrows(IOException::class.java) { drainOpened(dataSource) }
  }

  @Test
  fun reads_local_file_directly_without_touching_network_when_already_cached() {
    val localFile = writeTempFile(payload)
    val localUri = Uri.fromFile(localFile)

    every { mediaProvider.provideFileUri("book-1", "file-1") } returns
      OperationResult.Success(localUri)

    // Any read from this upstream blows up — it must never be used.
    val poisonedUpstream = FakeNetworkDataSource(payload, serveBeforeFailure = 0)
    val dataSource =
      LocalFallbackDataSource(
        upstream = poisonedUpstream,
        local = FileDataSource(),
        mediaProvider = mediaProvider,
      )

    val read = drainFully(dataSource)

    assertArrayEquals(payload, read)
    assertEquals(0, poisonedUpstream.openCount)
  }

  private fun drainFully(
    dataSource: DataSource,
    position: Long = 0,
  ): ByteArray {
    dataSource.open(
      DataSpec
        .Builder()
        .setUri(lissenUri)
        .setPosition(position)
        .build(),
    )
    return drainOpened(dataSource)
  }

  private fun drainOpened(dataSource: DataSource): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(256)
    try {
      while (true) {
        val read = dataSource.read(buffer, 0, buffer.size)
        if (read == C.RESULT_END_OF_INPUT) break
        out.write(buffer, 0, read)
      }
    } finally {
      dataSource.close()
    }
    return out.toByteArray()
  }

  private fun writeTempFile(content: ByteArray): File =
    File.createTempFile("lissen-fallback", ".bin").apply {
      writeBytes(content)
      deleteOnExit()
    }

  /**
   * Emulates a streamed source that delivers [serveBeforeFailure] bytes of [payload] (starting at
   * the requested [DataSpec.position]) and then fails every subsequent read with an [IOException],
   * as a dropped network connection would.
   */
  private class FakeNetworkDataSource(
    private val payload: ByteArray,
    private val serveBeforeFailure: Int,
  ) : DataSource {
    var openCount = 0
      private set

    private var startPosition = 0L
    private var served = 0
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
      openCount++
      uri = dataSpec.uri
      startPosition = dataSpec.position
      served = 0
      return payload.size - dataSpec.position
    }

    override fun read(
      buffer: ByteArray,
      offset: Int,
      length: Int,
    ): Int {
      if (served >= serveBeforeFailure) throw IOException("network connection lost")
      val from = (startPosition + served).toInt()
      val toCopy = minOf(length, serveBeforeFailure - served)
      System.arraycopy(payload, from, buffer, offset, toCopy)
      served += toCopy
      return toCopy
    }

    override fun getUri(): Uri? = uri

    override fun close() = Unit
  }
}
