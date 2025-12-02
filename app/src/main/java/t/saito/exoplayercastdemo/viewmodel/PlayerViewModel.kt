package t.saito.exoplayercastdemo.viewmodel

import android.app.Application
import android.net.Uri
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
import t.saito.exoplayercastdemo.service.PlaybackServiceConnection

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

    private var positionUpdateJob: Job? = null
    private var playerListener: Player.Listener? = null

    init {
        serviceConnection.bind()
        viewModelScope.launch {
            serviceConnection.service.collect { service ->
                service?.let {
                    setupPlayerListener(it.getPlayer())
                }
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

    fun playMedia(mediaItem: MediaItem) {
        _currentMedia.value = mediaItem
        serviceConnection.service.value?.playMedia(mediaItem.uri)
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

    private fun startPositionUpdate() {
        stopPositionUpdate()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                val player = serviceConnection.service.value?.getPlayer()
                player?.let {
                    _currentPosition.value = it.currentPosition
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
