package t.saito.exoplayercastdemo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import t.saito.exoplayercastdemo.MainActivity
import t.saito.exoplayercastdemo.R
import t.saito.exoplayercastdemo.util.Constants
import t.saito.exoplayercastdemo.data.model.MediaItem as AppMediaItem

class PlaybackService : Service() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private val binder = PlaybackServiceBinder()

    private var isForegroundService = false
    private var currentMediaUri: Uri? = null
    private var currentMediaItem: AppMediaItem? = null

    inner class PlaybackServiceBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize ExoPlayer
        player = ExoPlayer.Builder(this).build()

        // Initialize MediaSession
        mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
            isActive = true
        }

        // Connect MediaSession with ExoPlayer
        mediaSessionConnector = MediaSessionConnector(mediaSession).apply {
            setPlayer(player)
        }

        // Add player listener
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (player.playWhenReady) {
                            updateNotification()
                        }
                    }
                    Player.STATE_ENDED -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        isForegroundService = false
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotification()
            }
        })

        // Create notification channel
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        mediaSessionConnector.setPlayer(null)
        player.release()
        super.onDestroy()
    }

    fun getPlayer(): ExoPlayer = player

    // State restoration methods
    fun getCurrentMediaItem(): AppMediaItem? = currentMediaItem
    fun getCurrentMediaUri(): Uri? = currentMediaUri
    fun isCurrentlyPlaying(): Boolean = player.isPlaying
    fun getCurrentPosition(): Long = player.currentPosition
    fun getDuration(): Long = player.duration

    fun playMedia(mediaItem: AppMediaItem) {
        currentMediaItem = mediaItem
        currentMediaUri = mediaItem.uri
        val exoMediaItem = MediaItem.fromUri(mediaItem.uri)
        player.setMediaItem(exoMediaItem)
        player.prepare()
        player.play()

        if (!isForegroundService) {
            startForeground(Constants.NOTIFICATION_ID, createNotification())
            isForegroundService = true
        } else {
            updateNotification()
        }
    }

    fun pausePlayback() {
        player.pause()
        updateNotification()
    }

    fun resumePlayback() {
        player.play()
        updateNotification()
    }

    fun stopPlayback() {
        player.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForegroundService = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (player.isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PAUSE
                )
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    this,
                    PlaybackStateCompat.ACTION_PLAY
                )
            )
        }

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_delete,
            "Stop",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                this,
                PlaybackStateCompat.ACTION_STOP
            )
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Media Player")
            .setContentText("Playing media")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }

    private fun updateNotification() {
        if (isForegroundService) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(Constants.NOTIFICATION_ID, createNotification())
        }
    }
}
