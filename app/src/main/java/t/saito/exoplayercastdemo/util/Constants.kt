package t.saito.exoplayercastdemo.util

object Constants {
    // Remote media URLs
    const val REMOTE_AUDIO_URL = "https://storage.googleapis.com/uamp/Kai_Engel_-_Irsens_Tale/01_-_Intro_udonthear.mp3"
    const val REMOTE_VIDEO_URL = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4"

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "media_playback_channel"
    const val NOTIFICATION_ID = 1
    const val NOTIFICATION_CHANNEL_NAME = "Media Playback"

    // Local Media Server
    const val SERVER_DEFAULT_PORT = 8080
    const val SERVER_MAX_PORT_RETRY = 5
    const val SERVER_STARTUP_TIMEOUT_MS = 5000L
    const val SERVER_SHARED_PREFS = "local_media_server"
    const val SERVER_NOTIFICATION_ID = 2
    const val SERVER_NOTIFICATION_CHANNEL_ID = "local_media_server_channel"
    const val SERVER_NOTIFICATION_CHANNEL_NAME = "Local Media Server"

    // Server Actions
    const val ACTION_START_SERVER = "t.saito.exoplayercastdemo.ACTION_START_SERVER"
    const val ACTION_STOP_SERVER = "t.saito.exoplayercastdemo.ACTION_STOP_SERVER"
    const val ACTION_SERVER_STARTED = "t.saito.exoplayercastdemo.ACTION_SERVER_STARTED"
    const val ACTION_SERVER_START_FAILED = "t.saito.exoplayercastdemo.ACTION_SERVER_START_FAILED"

    // Server State Keys
    const val KEY_IS_RUNNING = "is_running"
    const val KEY_SERVER_PORT = "port"
    const val KEY_ERROR_MESSAGE = "error"
}
