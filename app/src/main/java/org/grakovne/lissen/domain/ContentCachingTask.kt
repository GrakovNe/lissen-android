package org.grakovne.lissen.domain

import java.io.Serializable

data class ContentCachingTask(
    val itemId: String,
    val options: DownloadOption,
    val currentPosition: Double,
) : Serializable
