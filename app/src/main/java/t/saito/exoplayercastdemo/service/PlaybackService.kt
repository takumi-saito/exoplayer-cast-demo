package t.saito.exoplayercastdemo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.cast.CastPlayer
import com.google.android.exoplayer2.ext.cast.SessionAvailabilityListener
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import t.saito.exoplayercastdemo.MainActivity
import t.saito.exoplayercastdemo.R
import t.saito.exoplayercastdemo.util.Constants
import t.saito.exoplayercastdemo.util.MediaServerUtils
import t.saito.exoplayercastdemo.util.NetworkUtils
import t.saito.exoplayercastdemo.data.model.MediaItem as AppMediaItem
import kotlin.coroutines.resume

class PlaybackService : Service() {
    private lateinit var exoPlayer: ExoPlayer
    private var castPlayer: CastPlayer? = null
    private lateinit var currentPlayer: Player
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var mediaSessionConnector: MediaSessionConnector
    private val binder = PlaybackServiceBinder()

    // キューマネージャー
    val queueManager = QueueManager()

    private var isForegroundService = false
    private var currentMediaUri: Uri? = null
    private var currentMediaItem: AppMediaItem? = null
    private var isCastSession = false

    // Local Media Server support
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var serverUrlContinuation: CancellableContinuation<String?>? = null
    private var isLocalServerRunning = false

    // BroadcastReceiver for server state notifications
    private val serverStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            android.util.Log.d("PlaybackService", "serverStateReceiver: action=${intent.action}")
            when (intent.action) {
                Constants.ACTION_SERVER_STARTED -> {
                    val port = intent.getIntExtra(Constants.KEY_SERVER_PORT, -1)
                    android.util.Log.d("PlaybackService", "Server started on port $port")
                    val ipAddress = NetworkUtils.getLocalIpAddress(context)
                    if (ipAddress != null && port > 0 && currentMediaUri != null) {
                        val url = MediaServerUtils.generateServerUrl(ipAddress, port, currentMediaUri!!)
                        android.util.Log.d("PlaybackService", "Generated server URL: $url")
                        isLocalServerRunning = true
                        serverUrlContinuation?.resume(url)
                    } else {
                        android.util.Log.e("PlaybackService", "Failed to generate server URL - ip=$ipAddress, port=$port")
                        serverUrlContinuation?.resume(null)
                    }
                    serverUrlContinuation = null
                }
                Constants.ACTION_SERVER_START_FAILED -> {
                    val error = intent.getStringExtra(Constants.KEY_ERROR_MESSAGE) ?: "Unknown error"
                    android.util.Log.e("PlaybackService", "Server start failed: $error")
                    serverUrlContinuation?.resume(null)
                    serverUrlContinuation = null
                }
            }
        }
    }

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
            // Use SafeMediaItemConverter to handle null values when converting MediaInfo
            val mediaItemConverter = t.saito.exoplayercastdemo.cast.SafeMediaItemConverter()
            castPlayer = CastPlayer(castContext, mediaItemConverter).apply {
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

        // Register BroadcastReceiver for local media server notifications
        val intentFilter = IntentFilter().apply {
            addAction(Constants.ACTION_SERVER_STARTED)
            addAction(Constants.ACTION_SERVER_START_FAILED)
        }
        // Android 14 (API 34)以降はRECEIVER_NOT_EXPORTEDが必須
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serverStateReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serverStateReceiver, intentFilter)
        }
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

                // Show error to user
                Toast.makeText(
                    this@PlaybackService,
                    "Cast再生エラー: ${error.message ?: "不明なエラー"}",
                    Toast.LENGTH_LONG
                ).show()

                // Fallback to local player
                android.util.Log.d("PlaybackService", "[CastPlayer] Falling back to local player due to error")
                switchToLocalPlayer()
            }
        }
    }

    private fun switchToCastPlayer() {
        android.util.Log.d("PlaybackService", "switchToCastPlayer() called")
        if (isCastSession) {
            android.util.Log.w("PlaybackService", "Already in cast session")
            return
        }

        // Check if current media is available
        val uri = currentMediaUri
        if (uri == null) {
            android.util.Log.w("PlaybackService", "No media to cast - currentMediaUri is null")
            return
        }

        android.util.Log.d("PlaybackService", "Current media URI: $uri")
        val scheme = uri.scheme?.lowercase()
        android.util.Log.d("PlaybackService", "URI scheme: $scheme")

        when (scheme) {
            "http", "https" -> {
                // Remote URL - cast directly
                proceedWithCast(uri)
            }
            "content" -> {
                // Local content - need local media server
                android.util.Log.d("PlaybackService", "Local content detected, starting local media server...")
                switchToCastPlayerAsync()
            }
            else -> {
                android.util.Log.w("PlaybackService", "Unsupported URI scheme: $scheme")
            }
        }
    }

    private fun switchToCastPlayerAsync() {
        serviceScope.launch {
            val uri = currentMediaUri ?: return@launch

            // Validate network availability
            if (!NetworkUtils.validateNetworkForCast(this@PlaybackService)) {
                android.util.Log.e("PlaybackService", "Network not available for Cast")
                handleServerStartFailure("Wi-Fi接続を確認してください")
                return@launch
            }

            // Start local media server
            val serverUrl = startLocalMediaServerAsync(uri)
            if (serverUrl == null) {
                android.util.Log.e("PlaybackService", "Failed to start local media server")
                handleServerStartFailure("ローカルメディアサーバーを起動できませんでした")
                return@launch
            }

            android.util.Log.d("PlaybackService", "Local media server started, URL: $serverUrl")

            // Proceed with cast using server URL
            proceedWithCast(Uri.parse(serverUrl))
        }
    }

    private suspend fun startLocalMediaServerAsync(contentUri: Uri): String? =
        suspendCancellableCoroutine { continuation ->
            serverUrlContinuation = continuation

            // Start local media server service
            val intent = Intent(this, LocalMediaServerService::class.java).apply {
                action = Constants.ACTION_START_SERVER
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            // Timeout handling
            serviceScope.launch {
                delay(Constants.SERVER_STARTUP_TIMEOUT_MS)
                if (continuation.isActive) {
                    android.util.Log.e("PlaybackService", "Server startup timeout")
                    continuation.resume(null)
                    serverUrlContinuation = null
                }
            }
        }

    private fun proceedWithCast(castableUri: Uri) {
        android.util.Log.d("PlaybackService", "proceedWithCast: $castableUri")

        val currentPosition = exoPlayer.currentPosition
        val playWhenReady = exoPlayer.playWhenReady
        android.util.Log.d("PlaybackService", "Switching to cast player - position: $currentPosition, playWhenReady: $playWhenReady")

        mediaSessionConnector.setPlayer(null)
        exoPlayer.playWhenReady = false

        isCastSession = true

        // Determine MIME type from original URI or MediaItem type
        val originalUri = currentMediaUri
        val mimeType = when {
            currentMediaItem?.type == t.saito.exoplayercastdemo.data.model.MediaType.AUDIO -> "audio/mpeg"
            currentMediaItem?.type == t.saito.exoplayercastdemo.data.model.MediaType.VIDEO -> "video/mp4"
            originalUri != null -> {
                val uriString = originalUri.toString().lowercase()
                when {
                    uriString.endsWith(".mp3") -> "audio/mpeg"
                    uriString.endsWith(".m4a") -> "audio/mp4"
                    uriString.endsWith(".aac") -> "audio/aac"
                    uriString.endsWith(".flac") -> "audio/flac"
                    uriString.endsWith(".wav") -> "audio/wav"
                    uriString.endsWith(".ogg") -> "audio/ogg"
                    uriString.endsWith(".mp4") -> "video/mp4"
                    uriString.endsWith(".webm") -> "video/webm"
                    uriString.endsWith(".mkv") -> "video/x-matroska"
                    uriString.endsWith(".avi") -> "video/x-msvideo"
                    uriString.endsWith(".mov") -> "video/quicktime"
                    else -> MediaServerUtils.getMimeType(this, originalUri)
                }
            }
            else -> "video/mp4"
        }
        android.util.Log.d("PlaybackService", "Determined MIME type: $mimeType (from originalUri: $originalUri, mediaItem.type: ${currentMediaItem?.type})")

        // Check if this is local media (served from local HTTP server)
        val isLocalMedia = castableUri.scheme == "http" && castableUri.host?.startsWith("192.168") == true
        android.util.Log.d("PlaybackService", "isLocalMedia: $isLocalMedia, uri: $castableUri")

        // Use RemoteMediaClient for ALL Cast playback to avoid CastPlayer queue sync issues
        proceedWithCastViaRemoteMediaClient(castableUri, mimeType, currentPosition, playWhenReady, isLocalMedia)
    }

    private fun proceedWithCastViaRemoteMediaClient(
        castableUri: Uri,
        mimeType: String,
        currentPosition: Long,
        playWhenReady: Boolean,
        isLocalMedia: Boolean
    ) {
        android.util.Log.d("PlaybackService", "Using RemoteMediaClient for Cast: $castableUri (isLocalMedia: $isLocalMedia)")

        val castSession = CastContext.getSharedInstance(this).sessionManager.currentCastSession
        val remoteMediaClient = castSession?.remoteMediaClient

        if (remoteMediaClient == null) {
            android.util.Log.e("PlaybackService", "RemoteMediaClient is null, cannot cast")
            switchToLocalPlayer()
            return
        }

        // Build Cast SDK MediaMetadata with thumbnail
        val castMetadata = MediaMetadata(
            if (currentMediaItem?.type == t.saito.exoplayercastdemo.data.model.MediaType.VIDEO)
                MediaMetadata.MEDIA_TYPE_MOVIE
            else
                MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
        ).apply {
            putString(MediaMetadata.KEY_TITLE, currentMediaItem?.title ?: "Unknown Title")
            currentMediaItem?.artist?.let { putString(MediaMetadata.KEY_ARTIST, it) }
            // サムネイル画像を追加
            currentMediaItem?.thumbnailUri?.let { thumbnailUri ->
                addImage(WebImage(thumbnailUri))
            }
        }

        // Build MediaInfo for Cast SDK
        val mediaInfo = MediaInfo.Builder(castableUri.toString())
            .setContentType(mimeType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(castMetadata)
            .build()

        // Determine start position - for local media start from beginning, for remote use current position
        val startPosition = if (isLocalMedia) 0L else currentPosition
        android.util.Log.d("PlaybackService", "Start position: $startPosition (isLocalMedia: $isLocalMedia)")

        // Load media using RemoteMediaClient directly
        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(playWhenReady)
            .setCurrentTime(startPosition)
            .build()

        // Don't use CastPlayer to avoid queue sync issues
        mediaSessionConnector.setPlayer(null)

        remoteMediaClient.load(loadRequest)
            .setResultCallback { result ->
                if (result.status.isSuccess) {
                    android.util.Log.d("PlaybackService", "RemoteMediaClient.load() succeeded")
                    updateNotification()
                } else {
                    android.util.Log.e("PlaybackService", "RemoteMediaClient.load() failed: ${result.status.statusMessage}")
                    switchToLocalPlayer()
                }
            }

        android.util.Log.d("PlaybackService", "RemoteMediaClient.load() request sent")
    }

    private fun handleServerStartFailure(message: String) {
        android.util.Log.e("PlaybackService", "Server start failure: $message")
        Toast.makeText(
            this,
            "ローカルメディアをキャストできません: $message",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun switchToLocalPlayer() {
        android.util.Log.d("PlaybackService", "switchToLocalPlayer() called")
        if (!isCastSession) return

        val castPlayerInstance = castPlayer ?: return
        val currentPosition = castPlayerInstance.currentPosition
        val playWhenReady = castPlayerInstance.playWhenReady

        mediaSessionConnector.setPlayer(null)
        castPlayerInstance.playWhenReady = false

        currentPlayer = exoPlayer
        isCastSession = false

        // Stop local media server if it was running
        stopLocalMediaServer()

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
        android.util.Log.d("PlaybackService", "switchToLocalPlayer() completed")
    }

    private fun stopLocalMediaServer() {
        if (isLocalServerRunning) {
            android.util.Log.d("PlaybackService", "Stopping local media server...")
            val intent = Intent(this, LocalMediaServerService::class.java).apply {
                action = Constants.ACTION_STOP_SERVER
            }
            startService(intent)
            isLocalServerRunning = false

            // Clear SharedPreferences
            getSharedPreferences(Constants.SERVER_SHARED_PREFS, MODE_PRIVATE)
                .edit().clear().apply()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_STICKY
    }

    override fun onDestroy() {
        android.util.Log.d("PlaybackService", "onDestroy()")

        // Unregister BroadcastReceiver
        try {
            unregisterReceiver(serverStateReceiver)
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Error unregistering receiver", e)
        }

        // Stop local media server
        stopLocalMediaServer()

        // Cancel coroutine scope
        serviceScope.cancel()

        // Clear SharedPreferences
        getSharedPreferences(Constants.SERVER_SHARED_PREFS, MODE_PRIVATE)
            .edit().clear().apply()

        // Release media components
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

        // If casting, load media via RemoteMediaClient
        if (isCastSession) {
            android.util.Log.d("PlaybackService", "playMedia during Cast session - loading via RemoteMediaClient")
            playMediaOnCast(mediaItem, mimeType)
            return
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

    private fun playMediaOnCast(mediaItem: AppMediaItem, mimeType: String) {
        val castSession = CastContext.getSharedInstance(this).sessionManager.currentCastSession
        val remoteMediaClient = castSession?.remoteMediaClient

        if (remoteMediaClient == null) {
            android.util.Log.e("PlaybackService", "RemoteMediaClient is null during Cast session")
            return
        }

        // Check if this is local media
        val isLocalMedia = mediaItem.uri.scheme == "content"

        if (isLocalMedia) {
            // For local media, need to start server and get URL
            android.util.Log.d("PlaybackService", "Local media during Cast - starting server")
            serviceScope.launch {
                val serverUrl = startLocalMediaServerAsync(mediaItem.uri)
                if (serverUrl != null) {
                    loadMediaOnRemoteClient(remoteMediaClient, Uri.parse(serverUrl), mimeType, mediaItem, true)
                } else {
                    android.util.Log.e("PlaybackService", "Failed to get server URL for local media")
                }
            }
        } else {
            // Remote media - load directly
            loadMediaOnRemoteClient(remoteMediaClient, mediaItem.uri, mimeType, mediaItem, false)
        }
    }

    private fun loadMediaOnRemoteClient(
        remoteMediaClient: com.google.android.gms.cast.framework.media.RemoteMediaClient,
        uri: Uri,
        mimeType: String,
        mediaItem: AppMediaItem,
        isLocalMedia: Boolean
    ) {
        android.util.Log.d("PlaybackService", "Loading media on RemoteMediaClient: $uri")

        // Build Cast SDK MediaMetadata with thumbnail
        val castMetadata = MediaMetadata(
            if (mediaItem.type == t.saito.exoplayercastdemo.data.model.MediaType.VIDEO)
                MediaMetadata.MEDIA_TYPE_MOVIE
            else
                MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
        ).apply {
            putString(MediaMetadata.KEY_TITLE, mediaItem.title)
            mediaItem.artist?.let { putString(MediaMetadata.KEY_ARTIST, it) }
            // サムネイル画像を追加
            mediaItem.thumbnailUri?.let { thumbnailUri ->
                addImage(WebImage(thumbnailUri))
            }
        }

        // Build MediaInfo for Cast SDK
        val mediaInfo = MediaInfo.Builder(uri.toString())
            .setContentType(mimeType)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(castMetadata)
            .build()

        val loadRequest = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(0L)
            .build()

        remoteMediaClient.load(loadRequest)
            .setResultCallback { result ->
                if (result.status.isSuccess) {
                    android.util.Log.d("PlaybackService", "Media loaded on Cast successfully")
                    updateNotification()
                } else {
                    android.util.Log.e("PlaybackService", "Failed to load media on Cast: ${result.status.statusMessage}")
                }
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

    // キュー操作メソッド

    /**
     * キューに追加
     */
    fun addToQueue(mediaItem: AppMediaItem) {
        queueManager.addToQueue(mediaItem)
    }

    /**
     * キューをセットして再生開始
     */
    fun playQueue(items: List<AppMediaItem>, startIndex: Int = 0) {
        queueManager.setQueue(items, startIndex)
        queueManager.getCurrentItem()?.let { playMedia(it) }
    }

    /**
     * 次の曲へ
     */
    fun skipToNext(): Boolean {
        val nextItem = queueManager.skipToNext()
        return if (nextItem != null) {
            playMedia(nextItem)
            true
        } else {
            false
        }
    }

    /**
     * 前の曲へ
     */
    fun skipToPrevious(): Boolean {
        val prevItem = queueManager.skipToPrevious()
        return if (prevItem != null) {
            playMedia(prevItem)
            true
        } else {
            false
        }
    }

    /**
     * キュー内の特定のインデックスにスキップ
     */
    fun skipToQueueItem(index: Int): Boolean {
        val item = queueManager.skipToIndex(index)
        return if (item != null) {
            playMedia(item)
            true
        } else {
            false
        }
    }

    /**
     * リピートモードをトグル
     */
    fun toggleRepeatMode(): QueueManager.RepeatMode {
        return queueManager.toggleRepeatMode()
    }

    /**
     * シャッフルをトグル
     */
    fun toggleShuffle(): Boolean {
        return queueManager.toggleShuffle()
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
