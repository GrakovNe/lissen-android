package org.grakovne.lissen.common

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.grakovne.lissen.R
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private val shareTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")

fun Context.shareFile(
  file: File,
  mimeType: String,
  chooserTitle: String,
  subjectLabel: String,
) {
  val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
  val timestamp = OffsetDateTime.now().format(shareTimestampFormatter)
  val sizeKb = file.length() / 1024

  val details = listOf(getString(R.string.app_name), timestamp, "$sizeKb KB")

  val shareIntent =
    Intent(Intent.ACTION_SEND).apply {
      type = mimeType
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, "$subjectLabel • $timestamp • $sizeKb KB")
      putExtra(Intent.EXTRA_TEXT, details.joinToString("\n"))
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

  startActivity(Intent.createChooser(shareIntent, chooserTitle))
}
