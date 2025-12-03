package t.saito.exoplayercastdemo.util

import android.content.Context
import android.net.Uri
import android.util.Base64

object MediaServerUtils {

    /**
     * MIMEタイプを判定
     * 1. ContentResolverから取得を試みる
     * 2. 拡張子から判定（フォールバック）
     */
    fun getMimeType(context: Context, uri: Uri): String {
        // ContentResolverから取得
        context.contentResolver.getType(uri)?.let { return it }

        // 拡張子から判定
        val uriString = uri.toString().lowercase()
        return when {
            uriString.endsWith(".mp3") -> "audio/mpeg"
            uriString.endsWith(".mp4") -> "video/mp4"
            uriString.endsWith(".m4a") -> "audio/mp4"
            uriString.endsWith(".aac") -> "audio/aac"
            uriString.endsWith(".flac") -> "audio/flac"
            uriString.endsWith(".wav") -> "audio/wav"
            uriString.endsWith(".ogg") -> "audio/ogg"
            uriString.endsWith(".webm") -> "video/webm"
            uriString.endsWith(".mkv") -> "video/x-matroska"
            uriString.endsWith(".avi") -> "video/x-msvideo"
            uriString.endsWith(".mov") -> "video/quicktime"
            uriString.endsWith(".3gp") -> "video/3gpp"
            else -> "application/octet-stream"
        }
    }

    /**
     * サーバーURLを生成
     * content:// URIをBase64 URL-SAFEエンコードしてパスに含める
     */
    fun generateServerUrl(ipAddress: String, port: Int, contentUri: Uri): String {
        val encodedUri = Base64.encodeToString(
            contentUri.toString().toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP
        )
        return "http://$ipAddress:$port/media/$encodedUri"
    }

    /**
     * エンコードされたmediaIdからcontent:// URIを復元
     */
    fun decodeMediaId(mediaId: String): Uri? {
        return try {
            val decoded = Base64.decode(mediaId, Base64.URL_SAFE)
            Uri.parse(String(decoded, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}
