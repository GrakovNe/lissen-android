package org.grakovne.lissen.domain

sealed interface DownloadOption

class NumberItemDownloadOption(val itemsNumber: Int) : DownloadOption
data object CurrentItemDownloadOption : DownloadOption
data object AllItemsDownloadOption : DownloadOption
