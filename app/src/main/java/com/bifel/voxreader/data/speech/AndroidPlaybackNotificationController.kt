package com.bifel.voxreader.data.speech

import android.content.Context
import com.bifel.voxreader.domain.speech.PlaybackCommand
import com.bifel.voxreader.domain.speech.PlaybackNotificationController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AndroidPlaybackNotificationController(
    private val context: Context,
) : PlaybackNotificationController {
    override val commands: Flow<PlaybackCommand> = MediaPlaybackService.commands.map { command ->
        when (command) {
            MediaPlaybackService.Command.Play -> PlaybackCommand.PLAY
            MediaPlaybackService.Command.Pause -> PlaybackCommand.PAUSE
            MediaPlaybackService.Command.Next -> PlaybackCommand.NEXT
            MediaPlaybackService.Command.Previous -> PlaybackCommand.PREVIOUS
        }
    }

    override fun update(title: String, playing: Boolean) =
        MediaPlaybackService.update(context, title, playing)

    override fun stop() = MediaPlaybackService.stop(context)
}
