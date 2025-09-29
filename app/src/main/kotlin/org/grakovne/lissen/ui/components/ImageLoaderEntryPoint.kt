package org.grakovne.lissen.ui.components

import coil3.ImageLoader
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageLoaderEntryPoint {
  fun getImageLoader(): ImageLoader
}
