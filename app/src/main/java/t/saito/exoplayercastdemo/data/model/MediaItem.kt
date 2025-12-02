package t.saito.exoplayercastdemo.data.model

import android.net.Uri

data class MediaItem(
    val id: String,
    val title: String,
    val artist: String? = null,
    val uri: Uri,
    val duration: Long = 0L,
    val type: MediaType
)
