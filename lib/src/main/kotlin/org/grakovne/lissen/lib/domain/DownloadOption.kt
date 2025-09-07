package org.grakovne.lissen.lib.domain

import androidx.annotation.Keep
import java.io.Serializable

@Keep
sealed interface DownloadOption : Serializable

class NumberItemDownloadOption(
  val itemsNumber: Int,
) : DownloadOption

data object CurrentItemDownloadOption : DownloadOption

data object RemainingItemsDownloadOption : DownloadOption

data object AllItemsDownloadOption : DownloadOption
