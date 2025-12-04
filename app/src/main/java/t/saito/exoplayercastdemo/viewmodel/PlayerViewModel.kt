package t.saito.exoplayercastdemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import t.saito.exoplayercastdemo.data.model.MediaItem
import t.saito.exoplayercastdemo.data.model.PlaybackState
import t.saito.exoplayercastdemo.service.PlaybackService
import t.saito.exoplayercastdemo.service.PlaybackServiceConnection
import t.saito.exoplayercastdemo.service.QueueManager

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val serviceConnection = PlaybackServiceConnection(application)

    private val _currentMedia = MutableStateFlow<MediaItem?>(null)
    val currentMedia: StateFlow<MediaItem?> = _currentMedia.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Stopped)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    // キュー関連
    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> = _queue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _repeatMode = MutableStateFlow(QueueManager.RepeatMode.OFF)
    val repeatMode: StateFlow<QueueManager.RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var playerListener: Player.Listener? = null

    init {
        serviceConnection.bind()
        viewModelScope.launch {
            serviceConnection.service.collect { service ->
                service?.let {
                    restorePlaybackState(it)
                    setupPlayerListener(it.getPlayer())
                    setupQueueObservers(it)
                }
            }
        }
    }

    private fun setupQueueObservers(service: PlaybackService) {
        viewModelScope.launch {
            service.queueManager.queue.collect { queueList ->
                _queue.value = queueList
            }
        }
        viewModelScope.launch {
            service.queueManager.currentIndex.collect { index ->
                _currentQueueIndex.value = index
            }
        }
        viewModelScope.launch {
            service.queueManager.repeatMode.collect { mode ->
                _repeatMode.value = mode
            }
        }
        viewModelScope.launch {
            service.queueManager.shuffleEnabled.collect { enabled ->
                _shuffleEnabled.value = enabled
            }
        }
    }

    private fun setupPlayerListener(player: Player) {
        playerListener?.let { player.removeListener(it) }

        playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        _playbackState.value = PlaybackState.Buffering()
                    }
                    Player.STATE_READY -> {
                        if (player.playWhenReady) {
                            _playbackState.value = PlaybackState.Playing
                            startPositionUpdate()
                        } else {
                            _playbackState.value = PlaybackState.Paused
                            stopPositionUpdate()
                        }
                        _duration.value = player.duration
                    }
                    Player.STATE_ENDED -> {
                        _playbackState.value = PlaybackState.Stopped
                        stopPositionUpdate()
                    }
                    Player.STATE_IDLE -> {
                        _playbackState.value = PlaybackState.Stopped
                        stopPositionUpdate()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _playbackState.value = PlaybackState.Playing
                    startPositionUpdate()
                } else {
                    _playbackState.value = PlaybackState.Paused
                    stopPositionUpdate()
                }
            }
        }.also { player.addListener(it) }
    }

    private fun restorePlaybackState(service: PlaybackService) {
        val currentItem = service.getCurrentMediaItem()
        if (currentItem != null) {
            _currentMedia.value = currentItem
            _currentPosition.value = service.getCurrentPosition()
            _duration.value = service.getDuration()
            _playbackState.value = if (service.isCurrentlyPlaying()) {
                PlaybackState.Playing
            } else {
                PlaybackState.Paused
            }
            if (service.isCurrentlyPlaying()) {
                startPositionUpdate()
            }
        }
        // Update Cast status
        _isCasting.value = service.isCasting()
    }

    fun playMedia(mediaItem: MediaItem) {
        _currentMedia.value = mediaItem
        serviceConnection.service.value?.playMedia(mediaItem)
    }

    fun togglePlayPause() {
        val service = serviceConnection.service.value ?: return
        val player = service.getPlayer()

        if (player.isPlaying) {
            service.pausePlayback()
        } else {
            service.resumePlayback()
        }
    }

    fun pause() {
        serviceConnection.service.value?.pausePlayback()
    }

    fun resume() {
        serviceConnection.service.value?.resumePlayback()
    }

    fun stop() {
        serviceConnection.service.value?.stopPlayback()
        _currentMedia.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun seekTo(position: Long) {
        serviceConnection.service.value?.getPlayer()?.seekTo(position)
        _currentPosition.value = position
    }

    // キュー操作メソッド

    /**
     * キューに追加
     */
    fun addToQueue(mediaItem: MediaItem) {
        serviceConnection.service.value?.addToQueue(mediaItem)
    }

    /**
     * キューをセットして再生開始
     */
    fun playQueue(items: List<MediaItem>, startIndex: Int = 0) {
        // 選択されたメディアを現在のメディアとして設定
        if (startIndex in items.indices) {
            _currentMedia.value = items[startIndex]
        }
        serviceConnection.service.value?.playQueue(items, startIndex)
    }

    /**
     * 次の曲へ
     */
    fun skipToNext(): Boolean {
        val result = serviceConnection.service.value?.skipToNext() ?: false
        if (result) {
            // 現在のメディアを更新
            _currentMedia.value = serviceConnection.service.value?.getCurrentMediaItem()
        }
        return result
    }

    /**
     * 前の曲へ
     */
    fun skipToPrevious(): Boolean {
        val result = serviceConnection.service.value?.skipToPrevious() ?: false
        if (result) {
            // 現在のメディアを更新
            _currentMedia.value = serviceConnection.service.value?.getCurrentMediaItem()
        }
        return result
    }

    /**
     * キュー内の特定のインデックスにスキップ
     */
    fun skipToQueueItem(index: Int): Boolean {
        val result = serviceConnection.service.value?.skipToQueueItem(index) ?: false
        if (result) {
            // 現在のメディアを更新
            _currentMedia.value = serviceConnection.service.value?.getCurrentMediaItem()
        }
        return result
    }

    /**
     * リピートモードをトグル
     */
    fun toggleRepeatMode(): QueueManager.RepeatMode {
        return serviceConnection.service.value?.toggleRepeatMode() ?: QueueManager.RepeatMode.OFF
    }

    /**
     * シャッフルをトグル
     */
    fun toggleShuffle(): Boolean {
        return serviceConnection.service.value?.toggleShuffle() ?: false
    }

    /**
     * 次の曲があるか
     */
    fun hasNext(): Boolean {
        return serviceConnection.service.value?.queueManager?.hasNext() ?: false
    }

    /**
     * 前の曲があるか
     */
    fun hasPrevious(): Boolean {
        return serviceConnection.service.value?.queueManager?.hasPrevious() ?: false
    }

    private fun startPositionUpdate() {
        stopPositionUpdate()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                val service = serviceConnection.service.value
                service?.let {
                    _currentPosition.value = it.getPlayer().currentPosition
                    _isCasting.value = it.isCasting()
                }
                delay(100)
            }
        }
    }

    private fun stopPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    override fun onCleared() {
        super.onCleared()
        playerListener?.let {
            serviceConnection.service.value?.getPlayer()?.removeListener(it)
        }
        stopPositionUpdate()
        serviceConnection.unbind()
    }
}
