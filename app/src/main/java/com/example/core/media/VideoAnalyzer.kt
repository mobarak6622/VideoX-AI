package com.example.core.media

import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.common.VideoMetadata
import java.io.File
import kotlin.math.abs

class VideoAnalyzer(private val context: Context) {

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "3gp", "m4v")
    }

    fun analyzeVideo(uri: Uri): VideoMetadata {
        val (resolvedName, resolvedSize) = queryFileNameAndSize(uri)
        val ext = resolvedName.substringAfterLast('.', "").lowercase()

        if (ext.isNotEmpty() && ext !in SUPPORTED_EXTENSIONS) {
            return VideoMetadata(
                uriString = uri.toString(),
                fileName = resolvedName,
                fileSizeBytes = resolvedSize,
                durationMs = 0L,
                width = 0,
                height = 0,
                frameRate = 0f,
                videoCodec = "Unsupported ($ext)",
                audioCodec = "N/A",
                bitrateBps = 0L,
                aspectRatioLabel = "N/A",
                rotationDegrees = 0,
                isHdr = false,
                hasAudio = false,
                isSupported = false,
                unsupportedReason = "Format '.$ext' is not in the supported container list (MP4, MOV, MKV, WEBM)."
            )
        }

        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        try {
            if (uri.scheme == "file") {
                val path = uri.path ?: ""
                retriever.setDataSource(path)
                extractor.setDataSource(path)
            } else {
                retriever.setDataSource(context, uri)
                extractor.setDataSource(context, uri, null)
            }

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            val hasAudioMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
            val colorTransfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull() ?: 0

            // Inspect tracks via MediaExtractor for exact codec MIME and frame rate
            var videoMime = "video/avc"
            var audioMime = "None"
            var detectedFps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
            var hasAudioTrack = hasAudioMeta

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoMime = mime
                    if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        val fpsVal = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                            .getOrElse { runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(0f) }
                        if (fpsVal > 0f) detectedFps = fpsVal
                    }
                } else if (mime.startsWith("audio/")) {
                    audioMime = mime
                    hasAudioTrack = true
                }
            }

            if (detectedFps <= 0f) {
                val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull() ?: 0
                detectedFps = if (frameCount > 0 && durationMs > 0) {
                    ((frameCount * 1000f) / durationMs).coerceIn(10f, 240f)
                } else {
                    30f
                }
            }

            val displayWidth = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
            val displayHeight = if (rotation == 90 || rotation == 270) rawWidth else rawHeight

            if (displayWidth <= 0 || displayHeight <= 0 || durationMs <= 0L) {
                return VideoMetadata(
                    uriString = uri.toString(),
                    fileName = resolvedName,
                    fileSizeBytes = resolvedSize,
                    durationMs = durationMs,
                    width = displayWidth,
                    height = displayHeight,
                    frameRate = detectedFps,
                    videoCodec = formatCodecLabel(videoMime),
                    audioCodec = formatCodecLabel(audioMime),
                    bitrateBps = bitrate,
                    aspectRatioLabel = "Unknown",
                    rotationDegrees = rotation,
                    isHdr = false,
                    hasAudio = hasAudioTrack,
                    isSupported = false,
                    unsupportedReason = "The selected video stream could not be decoded by the device's hardware MediaCodec."
                )
            }

            // HDR PQ (ST2084 = 6) or HLG (ARIB STD-B67 = 7)
            val isHdr = colorTransfer == 6 || colorTransfer == 7

            return VideoMetadata(
                uriString = uri.toString(),
                fileName = resolvedName,
                fileSizeBytes = resolvedSize,
                durationMs = durationMs,
                width = displayWidth,
                height = displayHeight,
                frameRate = detectedFps,
                videoCodec = formatCodecLabel(videoMime),
                audioCodec = if (hasAudioTrack) formatCodecLabel(audioMime) else "No Audio Track",
                bitrateBps = bitrate,
                aspectRatioLabel = computeAspectRatioLabel(displayWidth, displayHeight),
                rotationDegrees = rotation,
                isHdr = isHdr,
                hasAudio = hasAudioTrack,
                isSupported = true,
                unsupportedReason = null
            )
        } catch (e: Throwable) {
            return VideoMetadata(
                uriString = uri.toString(),
                fileName = resolvedName,
                fileSizeBytes = resolvedSize,
                durationMs = 0L,
                width = 0,
                height = 0,
                frameRate = 0f,
                videoCodec = "Unsupported",
                audioCodec = "None",
                bitrateBps = 0L,
                aspectRatioLabel = "N/A",
                rotationDegrees = 0,
                isHdr = false,
                hasAudio = false,
                isSupported = false,
                unsupportedReason = "Could not parse video container: ${e.localizedMessage ?: "Unsupported codec or corrupt file"}"
            )
        } finally {
            runCatching { retriever.release() }
            runCatching { extractor.release() }
        }
    }

    fun extractFrameAt(uri: Uri, timeUs: Long, maxDimension: Int = 720): Bitmap? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationMs = runCatching {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }.getOrDefault(0L)
            val safeMaxUs = ((durationMs * 1000L) - 50_000L).coerceAtLeast(0L)
            val clampedTimeUs = if (durationMs > 0L) timeUs.coerceIn(0L, safeMaxUs) else timeUs.coerceAtLeast(0L)

            val raw = runCatching {
                retriever.getFrameAtTime(clampedTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.getOrNull() ?: runCatching {
                retriever.getFrameAtTime(clampedTimeUs, MediaMetadataRetriever.OPTION_PREVIOUS_SYNC)
            }.getOrNull() ?: runCatching {
                retriever.getFrameAtTime(0L)
            }.getOrNull() ?: return null

            scaleDownIfNeeded(raw, maxDimension)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever?.release() }
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxDimension || maxDimension <= 0) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
        val scale = maxDimension.toFloat() / longest.toFloat()
        val targetW = (w * scale).toInt().coerceAtLeast(16)
        val targetH = (h * scale).toInt().coerceAtLeast(16)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun queryFileNameAndSize(uri: Uri): Pair<String, Long> {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            return Pair(file.name.ifBlank { "video_clip.mp4" }, if (file.exists()) file.length() else 0L)
        }
        var name = "imported_video.mp4"
        var size = 0L
        val cursor: Cursor? = runCatching {
            context.contentResolver.query(uri, null, null, null, null)
        }.getOrNull()
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0 && !c.isNull(nameIdx)) {
                    name = c.getString(nameIdx)
                }
                if (sizeIdx >= 0 && !c.isNull(sizeIdx)) {
                    size = c.getLong(sizeIdx)
                }
            }
        }
        return Pair(name, size)
    }

    private fun formatCodecLabel(mime: String): String = when (mime.lowercase()) {
        "video/avc" -> "H.264 / AVC"
        "video/hevc" -> "H.265 / HEVC"
        "video/av01" -> "AV1"
        "video/x-vnd.on2.vp9", "video/vp9" -> "VP9"
        "video/mp4v-es" -> "MPEG-4"
        "audio/mp4a-latm" -> "AAC Audio"
        "audio/opus" -> "Opus Audio"
        "audio/vorbis" -> "Vorbis Audio"
        "audio/ac3" -> "Dolby AC-3"
        else -> mime
    }

    private fun computeAspectRatioLabel(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return "Unknown"
        val ratio = w.toFloat() / h.toFloat()
        return when {
            abs(ratio - 16f / 9f) < 0.06f -> "16:9 Landscape"
            abs(ratio - 9f / 16f) < 0.06f -> "9:16 Portrait"
            abs(ratio - 1f) < 0.05f -> "1:1 Square"
            abs(ratio - 4f / 3f) < 0.06f -> "4:3 Classic"
            abs(ratio - 3f / 4f) < 0.06f -> "3:4 Vertical"
            abs(ratio - 21f / 9f) < 0.1f -> "21:9 Cinema"
            else -> String.format("%.2f:1", ratio)
        }
    }
}
