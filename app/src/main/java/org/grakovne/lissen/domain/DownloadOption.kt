package org.grakovne.lissen.domain

import java.io.Serializable

sealed interface DownloadOption : Serializable

class NumberItemDownloadOption(val itemsNumber: Int) : DownloadOption
data object CurrentItemDownloadOption : DownloadOption
data object AllItemsDownloadOption : DownloadOption
