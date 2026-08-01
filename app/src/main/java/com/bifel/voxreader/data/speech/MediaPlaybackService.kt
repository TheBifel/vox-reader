package com.bifel.voxreader.data.speech

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.PowerManager
import com.bifel.voxreader.MainActivity
import com.bifel.voxreader.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Keeps an active read visible to Android and forwards lock-screen/headset commands to the reader. */
class MediaPlaybackService : Service() {
    private lateinit var session: MediaSession
    private lateinit var playbackWakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        createChannel(this)
        playbackWakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:reader-playback")
            .apply { setReferenceCounted(false) }
        session = MediaSession(this, "VoxReader").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = emit(Command.Play)
                override fun onPause() = emit(Command.Pause)
                override fun onSkipToNext() = emit(Command.Next)
                override fun onSkipToPrevious() = emit(Command.Previous)
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val playing = intent?.getBooleanExtra(EXTRA_PLAYING, false) == true
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "VoxReader" }
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS,
                )
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0, 1f)
                .build(),
        )
        startForeground(NOTIFICATION_ID, notification(title, playing))
        if (playing && !playbackWakeLock.isHeld) {
            playbackWakeLock.acquire()
        } else if (!playing && playbackWakeLock.isHeld) {
            playbackWakeLock.release()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (::playbackWakeLock.isInitialized && playbackWakeLock.isHeld) playbackWakeLock.release()
        session.release()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(title: String, playing: Boolean): Notification {
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(if (playing) "Reading aloud" else "Reading paused")
            .setContentIntent(openApp)
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
            .build()
    }

    sealed interface Command { data object Play : Command; data object Pause : Command; data object Next : Command; data object Previous : Command }

    companion object {
        private const val CHANNEL_ID = "reader_playback"
        private const val NOTIFICATION_ID = 41
        private const val EXTRA_PLAYING = "playing"
        private const val EXTRA_TITLE = "title"
        private val commandEvents = MutableSharedFlow<Command>(extraBufferCapacity = 8)
        val commands: SharedFlow<Command> = commandEvents
        private fun emit(command: Command) { commandEvents.tryEmit(command) }
        fun update(context: Context, title: String, playing: Boolean) {
            context.startForegroundService(Intent(context, MediaPlaybackService::class.java).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_PLAYING, playing))
        }
        fun stop(context: Context) { context.stopService(Intent(context, MediaPlaybackService::class.java)) }
        private fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Reading playback", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
