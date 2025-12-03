package t.saito.exoplayercastdemo.server

import android.content.Context
import android.net.Uri
import android.util.Log
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import t.saito.exoplayercastdemo.util.MediaServerUtils
import java.io.FileNotFoundException

object MediaStreamHandler {
    private const val TAG = "MediaStreamHandler"

    suspend fun handleMediaRequest(call: ApplicationCall, context: Context, mediaId: String) {
        Log.d(TAG, "handleMediaRequest: mediaId=$mediaId")

        // Base64デコードしてcontent:// URIを復元
        val contentUri = MediaServerUtils.decodeMediaId(mediaId)
        if (contentUri == null) {
            Log.e(TAG, "Invalid mediaId - could not decode")
            call.respond(HttpStatusCode.BadRequest, "Invalid mediaId")
            return
        }

        Log.d(TAG, "Decoded URI: $contentUri")

        // ファイルサイズ取得
        val fileSize = getFileSize(context, contentUri)
        if (fileSize <= 0) {
            Log.e(TAG, "Media not found or empty: $contentUri")
            call.respond(HttpStatusCode.NotFound, "Media not found")
            return
        }

        Log.d(TAG, "File size: $fileSize bytes")

        val mimeType = MediaServerUtils.getMimeType(context, contentUri)
        Log.d(TAG, "MIME type: $mimeType")

        // Range Requestヘッダー解析
        val rangeHeader = call.request.headers[HttpHeaders.Range]
        Log.d(TAG, "Range header: $rangeHeader")

        try {
            if (rangeHeader != null) {
                // Range Request処理（シーク対応）
                handleRangeRequest(call, context, contentUri, fileSize, mimeType, rangeHeader)
            } else {
                // 全体配信
                handleFullRequest(call, context, contentUri, fileSize, mimeType)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for URI: $contentUri", e)
            call.respond(HttpStatusCode.Forbidden, "Permission denied")
        } catch (e: FileNotFoundException) {
            Log.e(TAG, "File not found: $contentUri", e)
            call.respond(HttpStatusCode.NotFound, "Media not found")
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming media: $contentUri", e)
            call.respond(HttpStatusCode.InternalServerError, "Error streaming media: ${e.message}")
        }
    }

    private suspend fun handleRangeRequest(
        call: ApplicationCall,
        context: Context,
        contentUri: Uri,
        fileSize: Long,
        mimeType: String,
        rangeHeader: String
    ) {
        // "bytes=0-1023" または "bytes=0-" 形式をパース
        val rangeMatch = Regex("bytes=(\\d*)-(\\d*)").find(rangeHeader)
        if (rangeMatch == null) {
            Log.e(TAG, "Invalid range header format: $rangeHeader")
            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        }

        val (startStr, endStr) = rangeMatch.destructured
        val start = startStr.toLongOrNull() ?: 0L
        val end = if (endStr.isEmpty()) fileSize - 1 else (endStr.toLongOrNull() ?: (fileSize - 1))

        if (start >= fileSize || end >= fileSize || start > end) {
            Log.e(TAG, "Invalid range: start=$start, end=$end, fileSize=$fileSize")
            call.response.header(HttpHeaders.ContentRange, "bytes */$fileSize")
            call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
            return
        }

        val contentLength = end - start + 1
        Log.d(TAG, "Range request: $start-$end/$fileSize (contentLength=$contentLength)")

        call.response.status(HttpStatusCode.PartialContent)
        call.response.header(HttpHeaders.ContentType, mimeType)
        call.response.header(HttpHeaders.ContentLength, contentLength.toString())
        call.response.header(HttpHeaders.AcceptRanges, "bytes")
        call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$fileSize")

        // AssetFileDescriptorでシーク対応のストリーム取得
        context.contentResolver.openAssetFileDescriptor(contentUri, "r")?.use { afd ->
            afd.createInputStream().use { inputStream ->
                // 開始位置までスキップ
                var skipped = 0L
                while (skipped < start) {
                    val s = inputStream.skip(start - skipped)
                    if (s <= 0) break
                    skipped += s
                }

                call.respondOutputStream(ContentType.parse(mimeType)) {
                    val buffer = ByteArray(8192)
                    var remaining = contentLength
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = inputStream.read(buffer, 0, toRead)
                        if (read == -1) break
                        write(buffer, 0, read)
                        remaining -= read
                    }
                }
            }
        } ?: run {
            Log.e(TAG, "Cannot open AssetFileDescriptor for: $contentUri")
            call.respond(HttpStatusCode.InternalServerError, "Cannot open file")
        }
    }

    private suspend fun handleFullRequest(
        call: ApplicationCall,
        context: Context,
        contentUri: Uri,
        fileSize: Long,
        mimeType: String
    ) {
        Log.d(TAG, "Full request: fileSize=$fileSize, mimeType=$mimeType")

        call.response.header(HttpHeaders.ContentType, mimeType)
        call.response.header(HttpHeaders.ContentLength, fileSize.toString())
        call.response.header(HttpHeaders.AcceptRanges, "bytes")

        context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
            call.respondOutputStream(ContentType.parse(mimeType)) {
                inputStream.copyTo(this, bufferSize = 8192)
            }
        } ?: run {
            Log.e(TAG, "Cannot open InputStream for: $contentUri")
            call.respond(HttpStatusCode.NotFound, "Media not found")
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                it.length
            } ?: -1L
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file size for: $uri", e)
            -1L
        }
    }
}
