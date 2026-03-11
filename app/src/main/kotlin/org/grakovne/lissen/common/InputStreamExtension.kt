package org.grakovne.lissen.common

import java.io.InputStream
import java.io.OutputStream

@Suppress("BlockingMethodInNonBlockingContext")
suspend fun InputStream.copyTo(
  out: OutputStream,
  onChunkCopied: suspend (chunkSize: Int) -> Unit,
) {
  var bytesCopied: Long = 0
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  var bytes = read(buffer)

  while (bytes >= 0) {
    out.write(buffer, 0, bytes)
    bytesCopied += bytes
    onChunkCopied(bytes)
    bytes = read(buffer)
  }
}
