package org.grakovne.lissen.content.cache.common

import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File

fun Buffer.writeToFile(file: File) {
  file.sink().buffer().use { sink ->
    sink.write(this, size)
    sink.flush()
  }
}
