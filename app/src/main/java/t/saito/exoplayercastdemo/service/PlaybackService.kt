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
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener
import com.google.android.gms.cast.framework.CastContext
import t.saito.exoplayercastdemo.MainActivity
import t.saito.exoplayercastdemo.R
import t.saito.exoplayercastdemo.util.Constants
import t.saito.exoplayercastdemo.data.model.MediaItem as AppMediaItem

class PlaybackService : Service() {
    private lateinit var exoPlayer: ExoPlayer
    private var castPlayer: CastPlayer? = null
    private lateinit var currentPlayer: Player
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private val binder = PlaybackServiceBinder()

    private var isForegroundService = false
    private var currentMediaUri: Uri? = null
    private var currentMediaItem: AppMediaItem? = null
    private var isCastSession = false

    inner class PlaybackServiceBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize ExoPlayer
        exoPlayer = ExoPlayer.Builder(this).build()
        currentPlayer = exoPlayer

        // Initialize CastPlayer if Cast is available
        try {
            val castContext = CastContext.getSharedInstance(this)
            castPlayer = CastPlayer(castContext).apply {
                setSessionAvailabilityListener(object : SessionAvailabilityListener {
                    override fun onCastSessionAvailable() {
                        switchToCastPlayer()
                    }

                    override fun onCastSessionUnavailable() {
                        switchToLocalPlayer()
                    }
                })
            }
        } catch (e: Exception) {
            // Cast not available (emulator or no Google Play Services)
            castPlayer = null
        }

        // Initialize MediaSession
        mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
            isActive = true
        }

        // Connect MediaSession with current player
        mediaSessionConnector = MediaSessionConnector(mediaSession).apply {
            setPlayer(currentPlayer)
        }

        // Add player listener
        exoPlayer.addListener(createPlayerListener())
        castPlayer?.addListener(createPlayerListener())

        // Create notification channel
        createNotificationChannel()
    }

    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (currentPlayer.playWhenReady) {
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
        }
    }

    private fun switchToCastPlayer() {
        if (isCastSession) return

        val currentPosition = exoPlayer.currentPosition
        val playWhenReady = exoPlayer.playWhenReady

        mediaSessionConnector.setPlayer(null)
        exoPlayer.playWhenReady = false

        currentPlayer = castPlayer ?: return
        isCastSession = true

        currentMediaUri?.let { uri ->
            // Determine mimeType based on media type
            val mimeType = when (currentMediaItem?.type) {
                t.saito.exoplayercastdemo.data.model.MediaType.VIDEO -> "video/*"
                t.saito.exoplayercastdemo.data.model.MediaType.AUDIO -> "audio/*"
                null -> "video/*"
            }

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(mimeType)
                .build()

            castPlayer?.setMediaItem(mediaItem)
            castPlayer?.seekTo(currentPosition)
            castPlayer?.playWhenReady = playWhenReady
            castPlayer?.prepare()
        }

        mediaSessionConnector.setPlayer(currentPlayer)
        updateNotification()
    }

    private fun switchToLocalPlayer() {
        if (!isCastSession) return

        val castPlayerInstance = castPlayer ?: return
        val currentPosition = castPlayerInstance.currentPosition
        val playWhenReady = castPlayerInstance.playWhenReady

        mediaSessionConnector.setPlayer(null)
        castPlayerInstance.playWhenReady = false

        currentPlayer = exoPlayer
        isCastSession = false

        currentMediaUri?.let { uri ->
            // Determine mimeType based on media type
            val mimeType = when (currentMediaItem?.type) {
                t.saito.exoplayercastdemo.data.model.MediaType.VIDEO -> "video/*"
                t.saito.exoplayercastdemo.data.model.MediaType.AUDIO -> "audio/*"
                null -> "video/*"
            }

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(mimeType)
                .build()

            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.seekTo(currentPosition)
            exoPlayer.playWhenReady = playWhenReady
            exoPlayer.prepare()
        }

        mediaSessionConnector.setPlayer(currentPlayer)
        updateNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession.release()
        mediaSessionConnector.setPlayer(null)
        exoPlayer.release()
        castPlayer?.release()
        super.onDestroy()
    }

    fun getPlayer(): Player = currentPlayer

    fun isCasting(): Boolean = isCastSession

    fun getCastPlayer(): CastPlayer? = castPlayer

    // State restoration methods
    fun getCurrentMediaItem(): AppMediaItem? = currentMediaItem
    fun getCurrentMediaUri(): Uri? = currentMediaUri
    fun isCurrentlyPlaying(): Boolean = currentPlayer.isPlaying
    fun getCurrentPosition(): Long = currentPlayer.currentPosition
    fun getDuration(): Long = currentPlayer.duration

    fun playMedia(mediaItem: AppMediaItem) {
        currentMediaItem = mediaItem
        currentMediaUri = mediaItem.uri
        val exoMediaItem = MediaItem.fromUri(mediaItem.uri)
        currentPlayer.setMediaItem(exoMediaItem)
        currentPlayer.prepare()
        currentPlayer.play()

        if (!isForegroundService) {
            startForeground(Constants.NOTIFICATION_ID, createNotification())
            isForegroundService = true
        } else {
            updateNotification()
        }
    }

    fun pausePlayback() {
        currentPlayer.pause()
        updateNotification()
    }

    fun resumePlayback() {
        currentPlayer.play()
        updateNotification()
    }

    fun stopPlayback() {
        currentPlayer.stop()
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

        val playPauseAction = if (currentPlayer.isPlaying) {
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
            .setContentTitle(if (isCastSession) "Casting" else "Media Player")
            .setContentText(currentMediaItem?.title ?: "Playing media")
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
