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
        exoPlayer.addListener(createPlayerListener("ExoPlayer"))
        castPlayer?.addListener(createCastPlayerListener())

        // Create notification channel
        createNotificationChannel()
    }

    private fun createPlayerListener(tag: String): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateString = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                android.util.Log.d("PlaybackService", "[$tag] onPlaybackStateChanged: $stateString")

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
                android.util.Log.d("PlaybackService", "[$tag] onIsPlayingChanged: $isPlaying")
                updateNotification()
            }

            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                android.util.Log.e("PlaybackService", "[$tag] onPlayerError: ${error.message}", error)
                android.util.Log.e("PlaybackService", "[$tag] Error code: ${error.errorCode}")
            }
        }
    }

    private fun createCastPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateString = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                android.util.Log.d("PlaybackService", "[CastPlayer] onPlaybackStateChanged: $stateString")

                when (playbackState) {
                    Player.STATE_IDLE -> {
                        android.util.Log.w("PlaybackService", "[CastPlayer] Player is IDLE - may indicate an error or no media loaded")
                    }
                    Player.STATE_READY -> {
                        android.util.Log.d("PlaybackService", "[CastPlayer] Player is READY - playWhenReady: ${castPlayer?.playWhenReady}")
                        if (currentPlayer.playWhenReady) {
                            updateNotification()
                        }
                    }
                    Player.STATE_ENDED -> {
                        android.util.Log.d("PlaybackService", "[CastPlayer] Playback ENDED")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        isForegroundService = false
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                android.util.Log.d("PlaybackService", "[CastPlayer] onIsPlayingChanged: $isPlaying")
                updateNotification()
            }

            override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                android.util.Log.e("PlaybackService", "[CastPlayer] onPlayerError: ${error.message}", error)
                android.util.Log.e("PlaybackService", "[CastPlayer] Error code: ${error.errorCode}")
                android.util.Log.e("PlaybackService", "[CastPlayer] Cause: ${error.cause}")
            }
        }
    }

    private fun switchToCastPlayer() {
        android.util.Log.d("PlaybackService", "switchToCastPlayer() called")
        if (isCastSession) {
            android.util.Log.w("PlaybackService", "Already in cast session")
            return
        }

        // Check if current media is castable (http or https only)
        currentMediaUri?.let { uri ->
            android.util.Log.d("PlaybackService", "Current media URI: $uri")
            val scheme = uri.scheme?.lowercase()
            android.util.Log.d("PlaybackService", "URI scheme: $scheme")
            if (scheme != "http" && scheme != "https") {
                // Cannot cast local media, stay on local player
                android.util.Log.w("PlaybackService", "Cannot cast local media with scheme: $scheme")
                return
            }
        } ?: run {
            // No media to cast
            android.util.Log.w("PlaybackService", "No media to cast - currentMediaUri is null")
            return
        }

        val currentPosition = exoPlayer.currentPosition
        val playWhenReady = exoPlayer.playWhenReady
        android.util.Log.d("PlaybackService", "Switching to cast player - position: $currentPosition, playWhenReady: $playWhenReady")

        mediaSessionConnector.setPlayer(null)
        exoPlayer.playWhenReady = false

        currentPlayer = castPlayer ?: return
        isCastSession = true

        currentMediaUri?.let { uri ->
            // Determine specific mimeType from URI extension
            val uriString = uri.toString().lowercase()
            val mimeType = when {
                uriString.endsWith(".mp4") -> "video/mp4"
                uriString.endsWith(".mp3") -> "audio/mpeg"
                uriString.endsWith(".m4a") -> "audio/mp4"
                uriString.endsWith(".webm") -> "video/webm"
                uriString.endsWith(".mkv") -> "video/x-matroska"
                currentMediaItem?.type == t.saito.exoplayercastdemo.data.model.MediaType.VIDEO -> "video/mp4"
                currentMediaItem?.type == t.saito.exoplayercastdemo.data.model.MediaType.AUDIO -> "audio/mpeg"
                else -> "video/mp4"
            }

            // Build MediaMetadata with title and artist
            val mediaMetadata = com.google.android.exoplayer2.MediaMetadata.Builder()
                .setTitle(currentMediaItem?.title ?: "Unknown Title")
                .setArtist(currentMediaItem?.artist)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(mimeType)
                .setMediaMetadata(mediaMetadata)
                .build()

            android.util.Log.d("PlaybackService", "Setting media item on CastPlayer - title: ${currentMediaItem?.title}, mimeType: $mimeType")
            castPlayer?.setMediaItem(mediaItem)
            castPlayer?.seekTo(currentPosition)
            castPlayer?.playWhenReady = playWhenReady
            castPlayer?.prepare()
            android.util.Log.d("PlaybackService", "CastPlayer prepared and ready to play")
        }

        mediaSessionConnector.setPlayer(currentPlayer)
        updateNotification()
        android.util.Log.d("PlaybackService", "switchToCastPlayer() completed successfully")
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
            // Determine specific mimeType from URI extension
            val uriString = uri.toString().lowercase()
            val mimeType = when {
                uriString.endsWith(".mp4") -> "video/mp4"
                uriString.endsWith(".mp3") -> "audio/mpeg"
                uriString.endsWith(".m4a") -> "audio/mp4"
                uriString.endsWith(".webm") -> "video/webm"
                uriString.endsWith(".mkv") -> "video/x-matroska"
                currentMediaItem?.type == t.saito.exoplayercastdemo.data.model.MediaType.VIDEO -> "video/mp4"
                currentMediaItem?.type == t.saito.exoplayercastdemo.data.model.MediaType.AUDIO -> "audio/mpeg"
                else -> "video/mp4"
            }

            // Build MediaMetadata with title and artist
            val mediaMetadata = com.google.android.exoplayer2.MediaMetadata.Builder()
                .setTitle(currentMediaItem?.title ?: "Unknown Title")
                .setArtist(currentMediaItem?.artist)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setMimeType(mimeType)
                .setMediaMetadata(mediaMetadata)
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

        // Determine specific mimeType from URI extension
        val uriString = mediaItem.uri.toString().lowercase()
        val mimeType = when {
            uriString.endsWith(".mp4") -> "video/mp4"
            uriString.endsWith(".mp3") -> "audio/mpeg"
            uriString.endsWith(".m4a") -> "audio/mp4"
            uriString.endsWith(".webm") -> "video/webm"
            uriString.endsWith(".mkv") -> "video/x-matroska"
            mediaItem.type == t.saito.exoplayercastdemo.data.model.MediaType.VIDEO -> "video/mp4"
            mediaItem.type == t.saito.exoplayercastdemo.data.model.MediaType.AUDIO -> "audio/mpeg"
            else -> "video/mp4"
        }

        // Build MediaMetadata with title and artist
        val mediaMetadata = com.google.android.exoplayer2.MediaMetadata.Builder()
            .setTitle(mediaItem.title)
            .setArtist(mediaItem.artist)
            .build()

        val exoMediaItem = MediaItem.Builder()
            .setUri(mediaItem.uri)
            .setMimeType(mimeType)
            .setMediaMetadata(mediaMetadata)
            .build()

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
