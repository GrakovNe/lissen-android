package org.grakovne.lissen.channel.common

interface ChannelProvider {
  fun provideMediaChannel(): MediaChannel

  fun provideChannelAuth(): ChannelAuthService

  fun getChannelCode(): ChannelCode
}
