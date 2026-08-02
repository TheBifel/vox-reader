package com.bifel.voxreader.domain.speech

import kotlinx.coroutines.flow.Flow

/** Platform notification and lock-screen control boundary for active reading playback. */
interface PlaybackNotificationController {
    val commands: Flow<PlaybackCommand>

    fun update(title: String, playing: Boolean)
    fun stop()
}

enum class PlaybackCommand {
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
}
