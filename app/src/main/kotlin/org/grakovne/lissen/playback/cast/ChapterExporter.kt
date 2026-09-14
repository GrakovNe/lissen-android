package org.grakovne.lissen.playback.cast

import android.content.Context
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.grakovne.lissen.playback.service.LissenDataSourceFactory
import org.grakovne.lissen.playback.service.toLissenUri
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Exports one chapter clip of a book file into a clean audio-only MP4 in the cache.
 *
 * Audiobook containers routinely carry cover art as video tracks and chapter text
 * tracks, which many DLNA renderers refuse. The platform [MediaExtractor] pulls just the
 * AAC samples and [MediaMuxer] writes them into a fresh MP4; nothing is re-encoded. The
 * source is read through the app's own data source, so server files stream in with the
 * bearer token and downloaded files come from disk.
 *
 * Returns null when the file carries no AAC track: such files are handed to the renderer
 * as they are.
 */
@Singleton
@UnstableApi
class ChapterExporter
  @Inject
  constructor(
    @param:ApplicationContext private val context: Context,
    private val dataSourceFactory: LissenDataSourceFactory,
  ) {
    private val directory = File(context.cacheDir, "cast")
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<File?>>()
    private val sequential = Mutex()

    suspend fun export(
      bookId: String,
      fileId: String,
      clipStartSeconds: Double,
      clipEndSeconds: Double,
    ): File? {
      val startMs = (clipStartSeconds * 1000).toLong().coerceAtLeast(0L)
      val endMs = (clipEndSeconds * 1000).toLong()
      val target = File(directory, "${key(bookId)}_${key(fileId)}_${startMs}_$endMs.m4a")

      if (target.exists() && target.length() > 0) return target

      val waiter = CompletableDeferred<File?>()
      val existing = inFlight.putIfAbsent(target.path, waiter)
      if (existing != null) return existing.await()

      try {
        directory.mkdirs()
        // One export at a time: parallel exports only split the bandwidth and delay the
        // piece that is needed right now.
        val result =
          withContext(Dispatchers.IO) {
            sequential.withLock { remux(bookId, fileId, startMs, endMs, target) }
          }
        waiter.complete(result)
        trimCache()
        return result
      } catch (e: Exception) {
        waiter.completeExceptionally(e)
        throw e
      } finally {
        inFlight.remove(target.path)
      }
    }

    fun clear() {
      directory.listFiles()?.filter { !inFlight.containsKey(it.path) }?.forEach { it.delete() }
    }

    /** Keeps the export cache bounded by dropping the oldest finished files first. */
    private fun trimCache() {
      val files = directory.listFiles()?.filter { it.name.endsWith(".m4a") }?.sortedBy { it.lastModified() } ?: return
      var total = files.sumOf { it.length() }
      for (file in files) {
        if (total <= MAX_CACHE_BYTES) break
        if (inFlight.containsKey(file.path)) continue
        total -= file.length()
        file.delete()
      }
    }

    private suspend fun remux(
      bookId: String,
      fileId: String,
      startMs: Long,
      endMs: Long,
      target: File,
    ): File? {
      val temp = File(target.path + ".part").also { it.delete() }
      val started = System.currentTimeMillis()
      val source = DataSourceMediaDataSource(dataSourceFactory.uncached(), toLissenUri(bookId, fileId))
      val extractor = MediaExtractor()
      var muxer: MediaMuxer? = null

      try {
        extractor.setDataSource(source)

        val trackIndex =
          (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC
          }

        if (trackIndex == null) {
          Timber.d("No AAC track in $fileId, renderer gets the original file")
          return null
        }

        val format = extractor.getTrackFormat(trackIndex)
        extractor.selectTrack(trackIndex)

        val startUs = startMs * 1000
        val endUs = if (endMs > startMs) endMs * 1000 else Long.MAX_VALUE
        if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val output = MediaMuxer(temp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
        val outputTrack = output.addTrack(format)
        output.start()

        val buffer = ByteBuffer.allocateDirect(SAMPLE_BUFFER_SIZE)
        val info = MediaCodec.BufferInfo()
        var written = 0L

        while (true) {
          coroutineContext.ensureActive()

          val size = extractor.readSampleData(buffer, 0)
          if (size < 0) break

          val timeUs = extractor.sampleTime
          if (timeUs >= endUs) break

          if (timeUs >= startUs) {
            val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            info.set(0, size, timeUs - startUs, flags)
            output.writeSampleData(outputTrack, buffer, info)
            written += size
          }

          if (!extractor.advance()) break
        }

        output.stop()
        muxer = null
        output.release()

        if (!temp.renameTo(target)) throw IOException("Unable to move exported chapter into place")

        Timber.d("Exported $fileId [$startMs-$endMs] in ${System.currentTimeMillis() - started} ms, $written bytes")
        return target
      } catch (e: Exception) {
        temp.delete()
        throw e
      } finally {
        runCatching { muxer?.release() }
        runCatching { extractor.release() }
        runCatching { source.close() }
      }
    }

    private fun key(value: String): String = value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(64)

    companion object {
      private const val MAX_CACHE_BYTES = 300L * 1024 * 1024
      private const val SAMPLE_BUFFER_SIZE = 1024 * 1024
    }
  }

/**
 * Adapts a Media3 [DataSource] to the platform [MediaDataSource]. Reads are mostly
 * sequential; the connection is kept open and re-opened at a new position only when
 * the extractor jumps elsewhere.
 */
@UnstableApi
internal class DataSourceMediaDataSource(
  private val factory: DataSource.Factory,
  private val uri: Uri,
) : MediaDataSource() {
  private var source: DataSource? = null
  private var position = -1L
  private var length = -1L

  override fun getSize(): Long {
    if (length < 0) open(0L)
    return length
  }

  override fun readAt(
    position: Long,
    buffer: ByteArray,
    offset: Int,
    size: Int,
  ): Int {
    if (size == 0) return 0
    if (source == null || position != this.position) open(position)

    val active = source ?: return -1
    var read = 0
    while (read < size) {
      val count = active.read(buffer, offset + read, size - read)
      if (count == C.RESULT_END_OF_INPUT) break
      read += count
      this.position += count
    }
    return if (read == 0) -1 else read
  }

  private fun open(at: Long) {
    close()
    val opened = factory.createDataSource()
    val remaining =
      opened.open(
        DataSpec
          .Builder()
          .setUri(uri)
          .setPosition(at)
          .build(),
      )
    source = opened
    position = at
    if (remaining != C.LENGTH_UNSET.toLong()) length = at + remaining
  }

  override fun close() {
    runCatching { source?.close() }
    source = null
  }
}
