package org.grakovne.lissen.widget

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import org.grakovne.lissen.common.RunningComponent

@Module
@InstallIn(SingletonComponent::class)
interface PlayerWidgetModule {

    @Binds
    @IntoSet
    fun bindPlayerWidgetStateService(service: PlayerWidgetStateService): RunningComponent
}
