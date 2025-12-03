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
import io.ktor.server.response.respondBytesWriter
import io.ktor.utils.io.writeFully
import t.saito.exoplayercastdemo.util.MediaServerUtils
import java.io.FileNotFoundException
import java.io.IOException

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
        } catch (e: IOException) {
            // Broken pipe は Cast デバイスが接続を閉じた場合に発生する（正常）
            if (e.message?.contains("Broken pipe") == true || e.message?.contains("Connection reset") == true) {
                Log.d(TAG, "Client closed connection (normal for Cast devices): ${e.message}")
            } else {
                Log.e(TAG, "IO error streaming media: $contentUri", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error streaming media: $contentUri", e)
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

        // AssetFileDescriptorでシーク対応のストリーム取得
        val afd = context.contentResolver.openAssetFileDescriptor(contentUri, "r")
        if (afd == null) {
            Log.e(TAG, "Cannot open AssetFileDescriptor for: $contentUri")
            call.respond(HttpStatusCode.InternalServerError, "Cannot open file")
            return
        }

        try {
            val inputStream = afd.createInputStream()
            try {
                // 開始位置までスキップ
                var skipped = 0L
                while (skipped < start) {
                    val toSkip = start - skipped
                    val s = inputStream.skip(toSkip)
                    if (s <= 0) {
                        val readByte = inputStream.read()
                        if (readByte == -1) {
                            Log.e(TAG, "EOF reached while skipping: skipped=$skipped, target=$start")
                            call.respond(HttpStatusCode.InternalServerError, "Failed to seek")
                            return
                        }
                        skipped++
                    } else {
                        skipped += s
                    }
                }

                Log.d(TAG, "Successfully skipped to position $start")

                // ヘッダーを設定してストリーミング
                call.response.status(HttpStatusCode.PartialContent)
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$fileSize")
                call.response.header(HttpHeaders.ContentLength, contentLength.toString())

                call.respondBytesWriter(contentType = ContentType.parse(mimeType)) {
                    val buffer = ByteArray(8192)
                    var remaining = contentLength
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = inputStream.read(buffer, 0, toRead)
                        if (read == -1) break
                        writeFully(buffer, 0, read)
                        remaining -= read
                    }
                }
            } finally {
                inputStream.close()
            }
        } finally {
            afd.close()
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

        val inputStream = context.contentResolver.openInputStream(contentUri)
        if (inputStream == null) {
            Log.e(TAG, "Cannot open InputStream for: $contentUri")
            call.respond(HttpStatusCode.NotFound, "Media not found")
            return
        }

        try {
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            call.response.header(HttpHeaders.ContentLength, fileSize.toString())

            call.respondBytesWriter(contentType = ContentType.parse(mimeType)) {
                val buffer = ByteArray(8192)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    writeFully(buffer, 0, read)
                }
            }
        } finally {
            inputStream.close()
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
