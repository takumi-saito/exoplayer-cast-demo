package t.saito.exoplayercastdemo.data.source

import android.net.Uri
import t.saito.exoplayercastdemo.data.model.MediaItem
import t.saito.exoplayercastdemo.data.model.MediaType
import t.saito.exoplayercastdemo.util.Constants

class RemoteMediaDataSource {
    fun getRemoteMedia(): List<MediaItem> {
        return listOf(
            MediaItem(
                id = "remote_audio",
                title = "Intro - udonthear",
                artist = "Kai Engel",
                uri = Uri.parse(Constants.REMOTE_AUDIO_URL),
                type = MediaType.AUDIO
            ),
            MediaItem(
                id = "remote_video",
                title = "Big Buck Bunny",
                artist = "Blender Foundation",
                uri = Uri.parse(Constants.REMOTE_VIDEO_URL),
                type = MediaType.VIDEO
            )
        )
    }
}
