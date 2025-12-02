package t.saito.exoplayercastdemo.repository

import t.saito.exoplayercastdemo.data.model.MediaItem

interface MediaRepository {
    suspend fun getAllMedia(): List<MediaItem>
    suspend fun getLocalMedia(): List<MediaItem>
    suspend fun getRemoteMedia(): List<MediaItem>
}
