package org.grakovne.lissen.playback

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.grakovne.lissen.domain.TimerOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackEventBus
  @Inject
  constructor() {
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    private val _commands = Channel<PlaybackCommand>(Channel.BUFFERED)
    val commands: Flow<PlaybackCommand> = _commands.receiveAsFlow()

    fun emit(event: PlaybackEvent) {
      _events.tryEmit(event)
    }

    fun send(command: PlaybackCommand) {
      _commands.trySendBlocking(command)
    }
  }

sealed class PlaybackEvent {
  data object PlaybackReady : PlaybackEvent()

  data object TimerExpired : PlaybackEvent()

  data class TimerTick(
    val remainingSeconds: Long,
  ) : PlaybackEvent()
}

sealed class PlaybackCommand {
  data object Play : PlaybackCommand()

  data object Pause : PlaybackCommand()

  data object PreparePlayback : PlaybackCommand()

  data class SeekTo(
    val position: Double,
  ) : PlaybackCommand()

  data class SetTimer(
    val delay: Double,
    val option: TimerOption,
  ) : PlaybackCommand()

  data object CancelTimer : PlaybackCommand()
}
