package t.saito.exoplayercastdemo.repository

import android.content.Context
import t.saito.exoplayercastdemo.data.model.MediaItem
import t.saito.exoplayercastdemo.data.source.LocalMediaDataSource
import t.saito.exoplayercastdemo.data.source.RemoteMediaDataSource

class MediaRepositoryImpl(context: Context) : MediaRepository {
    private val localDataSource = LocalMediaDataSource(context)
    private val remoteDataSource = RemoteMediaDataSource()

    override suspend fun getAllMedia(): List<MediaItem> {
        val remoteMedia = remoteDataSource.getRemoteMedia()
        val localAudio = localDataSource.getAudioFiles()
        val localVideo = localDataSource.getVideoFiles()

        return remoteMedia + localAudio + localVideo
    }

    override suspend fun getLocalMedia(): List<MediaItem> {
        val localAudio = localDataSource.getAudioFiles()
        val localVideo = localDataSource.getVideoFiles()
        return localAudio + localVideo
    }

    override suspend fun getRemoteMedia(): List<MediaItem> {
        return remoteDataSource.getRemoteMedia()
    }
}
