package org.grakovne.lissen.channel.common

import org.grakovne.lissen.common.LibraryOrderingConfiguration
import org.grakovne.lissen.common.LibraryOrderingOption

data class ChannelFilteringConfiguration(
    val orderingOptions: List<LibraryOrderingOption>,
    val defaultOrdering: LibraryOrderingConfiguration,
)
