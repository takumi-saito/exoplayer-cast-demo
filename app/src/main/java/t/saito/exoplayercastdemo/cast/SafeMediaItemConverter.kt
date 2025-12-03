package t.saito.exoplayercastdemo.cast

import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.MediaMetadata
import com.google.android.exoplayer2.ext.cast.MediaItemConverter
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem

/**
 * A MediaItemConverter that safely handles null values when converting
 * between Cast SDK MediaInfo and ExoPlayer MediaItem.
 *
 * This is needed when using RemoteMediaClient.load() directly instead of
 * CastPlayer.setMediaItem(), as the CastPlayer still listens to status
 * updates and tries to convert MediaInfo back to MediaItem.
 */
class SafeMediaItemConverter : MediaItemConverter {

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val mediaInfo = mediaQueueItem.media

        // Safely get content URL, defaulting to empty string if null
        val contentUrl = mediaInfo?.contentId ?: mediaInfo?.contentUrl ?: ""

        // Build MediaMetadata from Cast SDK metadata
        val mediaMetadataBuilder = MediaMetadata.Builder()
        mediaInfo?.metadata?.let { castMetadata ->
            castMetadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE)?.let {
                mediaMetadataBuilder.setTitle(it)
            }
            castMetadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST)?.let {
                mediaMetadataBuilder.setArtist(it)
            }
            castMetadata.getString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE)?.let {
                mediaMetadataBuilder.setAlbumTitle(it)
            }
        }

        return MediaItem.Builder()
            .setMediaId(contentUrl)
            .setUri(contentUrl)
            .setMimeType(mediaInfo?.contentType ?: "video/mp4")
            .setMediaMetadata(mediaMetadataBuilder.build())
            .build()
    }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        // Build Cast SDK MediaMetadata
        val castMetadata = com.google.android.gms.cast.MediaMetadata(
            com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MOVIE
        ).apply {
            mediaItem.mediaMetadata.title?.let {
                putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, it.toString())
            }
            mediaItem.mediaMetadata.artist?.let {
                putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, it.toString())
            }
        }

        // Build MediaInfo
        val mediaInfo = MediaInfo.Builder(mediaItem.localConfiguration?.uri?.toString() ?: "")
            .setContentType(mediaItem.localConfiguration?.mimeType ?: "video/mp4")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(castMetadata)
            .build()

        return MediaQueueItem.Builder(mediaInfo).build()
    }
}
