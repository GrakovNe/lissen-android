package org.grakovne.lissen.playback

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MediaRepositoryEntryPoint {
    fun mediaRepository(): MediaRepository
}