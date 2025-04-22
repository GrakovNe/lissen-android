package org.grakovne.lissen.channel.common

import androidx.annotation.Keep
import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingOption

@Keep
data class ChannelFilteringConfiguration(
    val orderingOptions: List<LibraryOrderingOption>,
    val defaultOrdering: LibraryOrderingConfiguration,
)
