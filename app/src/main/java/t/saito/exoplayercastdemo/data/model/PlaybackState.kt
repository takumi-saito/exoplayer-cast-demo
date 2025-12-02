package t.saito.exoplayercastdemo.data.model

sealed class PlaybackState {
    object Stopped : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    data class Buffering(val progress: Int = 0) : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}
