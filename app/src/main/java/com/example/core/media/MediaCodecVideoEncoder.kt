package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * Resilient, state-safe on-device MP4 video encoder that prevents
 * "Invalid to call at Released state; only valid in executing state" errors across
 * MediaTek, Qualcomm, Exynos, Tensor, and AOSP software codecs.
 *
 * Implements a 4-tier automatic fallback pipeline:
 *  1. Hardware/Default Encoder with COLOR_FormatSurface (GraphicBuffer Surface input)
 *  2. AOSP Software Encoder (c2.android.avc.encoder / OMX.google.h264.encoder) with 16-aligned YUV420 Image/ByteBuffer
 *  3. Hardware/Default Encoder with 16-aligned YUV420 Image/ByteBuffer and stride/sliceHeight padding
 *  4. Safe 720p HD fallback if high-resolution (4K/1440p/1080p) exceeds device codec memory
 */
object MediaCodecVideoEncoder {

    data class EncodeRequest(
        val context: Context,
        val outputFile: File,
        val requestedMime: String,
        val requestedWidth: Int,
        val requestedHeight: Int,
        val fps: Int,
        val bitrateBps: Int,
        val totalFrames: Int,
        val audioSourceUri: Uri? = null,
        val includeAudio: Boolean = false,
        val maxAudioDurationUs: Long = Long.MAX_VALUE,
        val isCancelled: () -> Boolean = { false },
        val isPaused: () -> Boolean = { false },
        val frameProvider: (frameIndex: Int, targetWidth: Int, targetHeight: Int) -> Bitmap,
        val onFrameEncoded: (frameIndex: Int, activeEncoderNote: String) -> Unit = { _, _ -> }
    )

    private data class EncoderAttemptConfig(
        val description: String,
        val mime: String,
        val width: Int,
        val height: Int,
        val bitrateBps: Int,
        val useSurfaceInput: Boolean,
        val preferSoftwareCodec: Boolean
    )

    fun alignTo16(value: Int): Int {
        val clamped = value.coerceAtLeast(64)
        val rem = clamped % 16
        return if (rem == 0) clamped else clamped + (16 - rem)
    }

    fun alignToEven(value: Int): Int {
        val clamped = value.coerceAtLeast(64)
        return if (clamped % 2 == 0) clamped else clamped + 1
    }

    fun encodeToMp4(request: EncodeRequest) {
        val evenW = alignToEven(request.requestedWidth)
        val evenH = alignToEven(request.requestedHeight)
        val aligned16W = alignTo16(request.requestedWidth)
        val aligned16H = alignTo16(request.requestedHeight)

        // Cap software encoder dimensions to 1920x1088 to stay within AOSP software AVC limits
        val (swW, swH) = computeClamped16Dimensions(aligned16W, aligned16H, 1920, 1088)
        val (hdW, hdH) = computeClamped16Dimensions(aligned16W, aligned16H, 1280, 720)

        val attempts = listOf(
            EncoderAttemptConfig(
                description = "Hardware Surface (${evenW}×${evenH})",
                mime = request.requestedMime,
                width = evenW,
                height = evenH,
                bitrateBps = request.bitrateBps,
                useSurfaceInput = true,
                preferSoftwareCodec = false
            ),
            EncoderAttemptConfig(
                description = "AOSP Software YUV420 (${swW}×${swH})",
                mime = MediaFormat.MIMETYPE_VIDEO_AVC,
                width = swW,
                height = swH,
                bitrateBps = request.bitrateBps.coerceAtMost(16_000_000),
                useSurfaceInput = false,
                preferSoftwareCodec = true
            ),
            EncoderAttemptConfig(
                description = "Aligned Hardware YUV420 (${aligned16W}×${aligned16H})",
                mime = MediaFormat.MIMETYPE_VIDEO_AVC,
                width = aligned16W,
                height = aligned16H,
                bitrateBps = request.bitrateBps.coerceAtMost(20_000_000),
                useSurfaceInput = false,
                preferSoftwareCodec = false
            ),
            EncoderAttemptConfig(
                description = "Safe HD Surface (${hdW}×${hdH})",
                mime = MediaFormat.MIMETYPE_VIDEO_AVC,
                width = hdW,
                height = hdH,
                bitrateBps = request.bitrateBps.coerceAtMost(8_000_000),
                useSurfaceInput = true,
                preferSoftwareCodec = false
            ),
            EncoderAttemptConfig(
                description = "Safe HD Software YUV420 (${hdW}×${hdH})",
                mime = MediaFormat.MIMETYPE_VIDEO_AVC,
                width = hdW,
                height = hdH,
                bitrateBps = request.bitrateBps.coerceAtMost(8_000_000),
                useSurfaceInput = false,
                preferSoftwareCodec = true
            )
        )

        var lastError: Throwable? = null
        for (attempt in attempts) {
            if (request.isCancelled()) {
                throw IllegalStateException("Processing cancelled by user.")
            }
            try {
                if (request.outputFile.exists()) {
                    request.outputFile.delete()
                }
                runSingleEncodeAttempt(request, attempt)
                if (request.outputFile.exists() && request.outputFile.length() >= 256L) {
                    return
                }
            } catch (t: Throwable) {
                if (request.isCancelled()) {
                    throw t
                }
                lastError = t
            }
        }

        throw IllegalStateException(
            "Unable to encode video stream on this device: ${lastError?.localizedMessage ?: "All encoder tiers failed."}",
            lastError
        )
    }

    private fun computeClamped16Dimensions(
        width: Int,
        height: Int,
        maxLongEdge: Int,
        maxShortEdge: Int
    ): Pair<Int, Int> {
        val isPortrait = height > width
        val longEdge = maxOf(width, height)
        val shortEdge = minOf(width, height)
        if (longEdge <= maxLongEdge && shortEdge <= maxShortEdge) {
            return Pair(alignTo16(width), alignTo16(height))
        }
        val scale = minOf(
            maxLongEdge.toDouble() / longEdge.toDouble(),
            maxShortEdge.toDouble() / shortEdge.toDouble()
        )
        val scaledW = alignTo16((width * scale).toInt())
        val scaledH = alignTo16((height * scale).toInt())
        return if (isPortrait) {
            Pair(minOf(scaledW, maxShortEdge), minOf(scaledH, maxLongEdge))
        } else {
            Pair(minOf(scaledW, maxLongEdge), minOf(scaledH, maxShortEdge))
        }
    }

    private fun findEncoderName(
        mime: String,
        preferSoftware: Boolean,
        requireSurfaceColorFormat: Boolean
    ): Pair<String?, Int> {
        val codecInfos = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
        }.getOrDefault(emptyList())

        if (codecInfos.isEmpty()) {
            val defaultColor = if (requireSurfaceColorFormat) {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            } else {
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            }
            return Pair(null, defaultColor)
        }

        val sorted = if (preferSoftware) {
            codecInfos.sortedByDescending { isSoftwareCodec(it) }
        } else {
            codecInfos.sortedBy { isSoftwareCodec(it) }
        }

        for (info in sorted) {
            val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
            val colorFormats = caps.colorFormats ?: IntArray(0)
            if (requireSurfaceColorFormat) {
                if (colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)) {
                    return Pair(info.name, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                }
            } else {
                val chosenColor = when {
                    colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) ->
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) ->
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                    colorFormats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) ->
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                    else -> -1
                }
                if (chosenColor != -1) {
                    return Pair(info.name, chosenColor)
                }
            }
        }

        val fallbackColor = if (requireSurfaceColorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        } else {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        }
        return Pair(sorted.firstOrNull()?.name, fallbackColor)
    }

    private fun isSoftwareCodec(info: MediaCodecInfo): Boolean {
        val name = info.name.lowercase()
        if (name.startsWith("c2.android.") || name.startsWith("omx.google.") || name.contains(".sw.")) {
            return true
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.isSoftwareOnly
        } else {
            false
        }
    }

    private fun runSingleEncodeAttempt(
        request: EncodeRequest,
        config: EncoderAttemptConfig
    ) {
        val fps = request.fps.coerceIn(12, 120)
        val totalFrames = request.totalFrames.coerceAtLeast(1)

        val (encoderName, colorFormat) = findEncoderName(
            mime = config.mime,
            preferSoftware = config.preferSoftwareCodec,
            requireSurfaceColorFormat = config.useSurfaceInput
        )

        val format = MediaFormat.createVideoFormat(config.mime, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        var encoder: MediaCodec? = null
        var inputSurface: android.view.Surface? = null
        var isEncoderExecuting = false
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var audioExtractor: MediaExtractor? = null

        try {
            encoder = if (encoderName != null) {
                runCatching { MediaCodec.createByCodecName(encoderName) }
                    .getOrElse { MediaCodec.createEncoderByType(config.mime) }
            } else {
                MediaCodec.createEncoderByType(config.mime)
            }

            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            if (config.useSurfaceInput) {
                inputSurface = encoder.createInputSurface()
            }
            encoder.start()
            isEncoderExecuting = true

            val activeMuxer = MediaMuxer(
                request.outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = activeMuxer

            // Optional original audio track setup
            var muxerAudioTrackIndex = -1
            if (request.includeAudio && request.audioSourceUri != null) {
                runCatching {
                    val ex = MediaExtractor()
                    val uri = request.audioSourceUri
                    if (uri.scheme == "file") {
                        ex.setDataSource(uri.path ?: "")
                    } else {
                        ex.setDataSource(request.context, uri, null)
                    }
                    for (t in 0 until ex.trackCount) {
                        val fmt = ex.getTrackFormat(t)
                        val trackMime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                        if (trackMime.startsWith("audio/")) {
                            ex.selectTrack(t)
                            muxerAudioTrackIndex = activeMuxer.addTrack(fmt)
                            audioExtractor = ex
                            break
                        }
                    }
                }
            }

            var muxerVideoTrackIndex = -1
            var writtenVideoSamples = 0L
            var lastMuxedPtsUs = -1L
            val bufferInfo = MediaCodec.BufferInfo()
            val surfacePaint = Paint(Paint.FILTER_BITMAP_FLAG)

            fun drainEncoderOutputs(waitUntilEos: Boolean) {
                if (!isEncoderExecuting) return
                var emptyTries = 0
                val maxEmptyTries = if (waitUntilEos) 80 else 3

                while (isEncoderExecuting) {
                    val outStatus = try {
                        encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    } catch (t: Throwable) {
                        isEncoderExecuting = false
                        throw t
                    }

                    if (outStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        emptyTries++
                        if (!waitUntilEos || emptyTries >= maxEmptyTries) {
                            break
                        }
                    } else if (outStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            muxerVideoTrackIndex = activeMuxer.addTrack(encoder.outputFormat)
                            activeMuxer.start()
                            muxerStarted = true
                        }
                    } else if (outStatus >= 0) {
                        emptyTries = 0
                        val encodedData = try {
                            encoder.getOutputBuffer(outStatus)
                        } catch (t: Throwable) {
                            isEncoderExecuting = false
                            throw t
                        }

                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size > 0 && muxerStarted && encodedData != null) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)

                            // Guarantee strictly increasing deterministic presentation timestamps
                            val deterministicPtsUs = (writtenVideoSamples * 1_000_000L) / fps
                            bufferInfo.presentationTimeUs = if (deterministicPtsUs <= lastMuxedPtsUs) {
                                lastMuxedPtsUs + (1_000_000L / fps).coerceAtLeast(1_000L)
                            } else {
                                deterministicPtsUs
                            }
                            lastMuxedPtsUs = bufferInfo.presentationTimeUs

                            activeMuxer.writeSampleData(muxerVideoTrackIndex, encodedData, bufferInfo)
                            writtenVideoSamples++
                        }

                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        try {
                            encoder.releaseOutputBuffer(outStatus, false)
                        } catch (t: Throwable) {
                            isEncoderExecuting = false
                            throw t
                        }

                        if (isEos) {
                            break
                        }
                    }
                }
            }

            if (config.useSurfaceInput && inputSurface != null) {
                for (frameIdx in 0 until totalFrames) {
                    if (request.isCancelled()) {
                        throw IllegalStateException("Processing cancelled by user.")
                    }
                    while (request.isPaused() && !request.isCancelled()) {
                        Thread.sleep(100)
                    }

                    drainEncoderOutputs(waitUntilEos = false)

                    val bmp = request.frameProvider(frameIdx, config.width, config.height)
                    try {
                        val canvas: Canvas = inputSurface.lockCanvas(null)
                        try {
                            val dstRect = Rect(0, 0, canvas.width, canvas.height)
                            canvas.drawBitmap(bmp, null, dstRect, surfacePaint)
                        } finally {
                            inputSurface.unlockCanvasAndPost(canvas)
                        }
                    } finally {
                        bmp.recycle()
                    }

                    // Brief 2ms pacing ensures distinct monotonic surface timestamps for hardware rate control
                    Thread.sleep(2)
                    drainEncoderOutputs(waitUntilEos = false)
                    request.onFrameEncoded(frameIdx, config.description)
                }

                if (isEncoderExecuting) {
                    try {
                        encoder.signalEndOfInputStream()
                    } catch (t: Throwable) {
                        isEncoderExecuting = false
                        throw t
                    }
                    drainEncoderOutputs(waitUntilEos = true)
                }
            } else {
                // ByteBuffer / Image input mode with stride & sliceHeight alignment
                val inputFormat = runCatching { encoder.inputFormat }.getOrNull()
                val stride = runCatching {
                    inputFormat?.getInteger(MediaFormat.KEY_STRIDE) ?: config.width
                }.getOrDefault(config.width).coerceAtLeast(config.width)
                val sliceHeight = runCatching {
                    inputFormat?.getInteger(MediaFormat.KEY_SLICE_HEIGHT) ?: config.height
                }.getOrDefault(config.height).coerceAtLeast(config.height)

                for (frameIdx in 0..totalFrames) {
                    if (request.isCancelled()) {
                        throw IllegalStateException("Processing cancelled by user.")
                    }
                    while (request.isPaused() && !request.isCancelled()) {
                        Thread.sleep(100)
                    }

                    var queued = false
                    var retries = 0
                    while (!queued && isEncoderExecuting && retries < 50) {
                        drainEncoderOutputs(waitUntilEos = false)

                        val inBufIdx = try {
                            encoder.dequeueInputBuffer(10_000)
                        } catch (t: Throwable) {
                            isEncoderExecuting = false
                            throw t
                        }

                        if (inBufIdx >= 0) {
                            val ptsUs = (frameIdx * 1_000_000L) / fps
                            if (frameIdx < totalFrames) {
                                val bmp = request.frameProvider(frameIdx, config.width, config.height)
                                try {
                                    val queuedBytes = fillInputBufferOrImage(
                                        encoder = encoder,
                                        inputBufferIndex = inBufIdx,
                                        bitmap = bmp,
                                        width = config.width,
                                        height = config.height,
                                        stride = stride,
                                        sliceHeight = sliceHeight,
                                        colorFormat = colorFormat
                                    )
                                    encoder.queueInputBuffer(inBufIdx, 0, queuedBytes, ptsUs, 0)
                                } catch (t: Throwable) {
                                    isEncoderExecuting = false
                                    throw t
                                } finally {
                                    bmp.recycle()
                                }
                                request.onFrameEncoded(frameIdx, config.description)
                            } else {
                                try {
                                    encoder.queueInputBuffer(
                                        inBufIdx,
                                        0,
                                        0,
                                        ptsUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                } catch (t: Throwable) {
                                    isEncoderExecuting = false
                                    throw t
                                }
                            }
                            queued = true
                        } else {
                            retries++
                        }
                    }

                    drainEncoderOutputs(waitUntilEos = frameIdx == totalFrames)
                }
            }

            if (!muxerStarted || writtenVideoSamples <= 0L) {
                error("Encoder produced 0 video packets for ${config.description}")
            }

            // Mux original audio stream if requested and available
            val activeAudioExtractor = audioExtractor
            if (muxerStarted && activeAudioExtractor != null && muxerAudioTrackIndex >= 0) {
                runCatching {
                    val audioBuf = ByteBuffer.allocate(64 * 1024)
                    val audioInfo = MediaCodec.BufferInfo()
                    var lastAudioPtsUs = -1L
                    while (true) {
                        audioInfo.offset = 0
                        val sampleSize = activeAudioExtractor.readSampleData(audioBuf, 0)
                        if (sampleSize < 0) break
                        val sampleTimeUs = activeAudioExtractor.sampleTime
                        if (sampleTimeUs < 0L || sampleTimeUs > request.maxAudioDurationUs) break
                        if (sampleTimeUs > lastAudioPtsUs) {
                            audioInfo.size = sampleSize
                            audioInfo.presentationTimeUs = sampleTimeUs
                            audioInfo.flags = activeAudioExtractor.sampleFlags
                            activeMuxer.writeSampleData(muxerAudioTrackIndex, audioBuf, audioInfo)
                            lastAudioPtsUs = sampleTimeUs
                        }
                        if (!activeAudioExtractor.advance()) break
                    }
                }
            }
        } finally {
            runCatching { audioExtractor?.release() }
            runCatching { inputSurface?.release() }
            if (isEncoderExecuting && encoder != null) {
                runCatching { encoder.stop() }
                isEncoderExecuting = false
            }
            if (encoder != null) {
                runCatching { encoder.release() }
            }
            if (muxerStarted && muxer != null) {
                runCatching { muxer.stop() }
            }
            if (muxer != null) {
                runCatching { muxer.release() }
            }
        }
    }

    private fun fillInputBufferOrImage(
        encoder: MediaCodec,
        inputBufferIndex: Int,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        stride: Int,
        sliceHeight: Int,
        colorFormat: Int
    ): Int {
        val scaledBitmap = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }

        val argb = IntArray(width * height)
        scaledBitmap.getPixels(argb, 0, width, 0, 0, width, height)
        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }

        val rawBuf = encoder.getInputBuffer(inputBufferIndex)
        val inputImage: Image? = runCatching { encoder.getInputImage(inputBufferIndex) }.getOrNull()

        if (inputImage != null && inputImage.planes.size >= 3) {
            val yPlane = inputImage.planes[0]
            val uPlane = inputImage.planes[1]
            val vPlane = inputImage.planes[2]

            val yBuf = yPlane.buffer
            val uBuf = uPlane.buffer
            val vBuf = vPlane.buffer

            val yRowStride = yPlane.rowStride
            val yPixelStride = yPlane.pixelStride
            val uRowStride = uPlane.rowStride
            val uPixelStride = uPlane.pixelStride
            val vRowStride = vPlane.rowStride
            val vPixelStride = vPlane.pixelStride

            val yLimit = yBuf.limit()
            val uLimit = uBuf.limit()
            val vLimit = vBuf.limit()

            for (j in 0 until height) {
                val rowOffset = j * width
                val yRowOffset = j * yRowStride
                val uvRowOffsetU = (j shr 1) * uRowStride
                val uvRowOffsetV = (j shr 1) * vRowStride
                val isEvenRow = (j and 1) == 0

                for (i in 0 until width) {
                    val px = argb[rowOffset + i]
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF

                    val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                    val yIdx = yRowOffset + i * yPixelStride
                    if (yIdx in 0 until yLimit) {
                        yBuf.put(yIdx, y)
                    }

                    if (isEvenRow && (i and 1) == 0) {
                        val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                        val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                        val uvCol = i shr 1
                        val uIdx = uvRowOffsetU + uvCol * uPixelStride
                        val vIdx = uvRowOffsetV + uvCol * vPixelStride
                        if (uIdx in 0 until uLimit) {
                            uBuf.put(uIdx, u)
                        }
                        if (vIdx in 0 until vLimit) {
                            vBuf.put(vIdx, v)
                        }
                    }
                }
            }

            val expectedSize = (stride * sliceHeight * 3) / 2
            val cap = rawBuf?.capacity() ?: expectedSize
            return expectedSize.coerceIn(width * height * 3 / 2, cap)
        }

        // Fallback: Direct stride-aware ByteBuffer filling
        if (rawBuf == null) return 0
        rawBuf.clear()
        val capacity = rawBuf.capacity()
        val effectiveStride = if (stride >= width) stride else width
        val effectiveSliceHeight = if (sliceHeight >= height) sliceHeight else height
        val totalFrameBytes = ((effectiveStride * effectiveSliceHeight * 3) / 2).coerceAtMost(capacity)
        val yuv = ByteArray(totalFrameBytes)

        // Default neutral chroma (128) for any padded sliceHeight/stride rows
        val uvBase = effectiveStride * effectiveSliceHeight
        for (idx in uvBase until totalFrameBytes) {
            yuv[idx] = 128.toByte()
        }

        val isPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        val uPlaneBase = uvBase
        val vPlaneBase = uvBase + (effectiveStride * effectiveSliceHeight) / 4

        for (j in 0 until height) {
            val srcRow = j * width
            val dstYRow = j * effectiveStride
            val isEvenRow = (j and 1) == 0

            for (i in 0 until width) {
                val px = argb[srcRow + i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF

                val y = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                val yPos = dstYRow + i
                if (yPos < uvBase && yPos < totalFrameBytes) {
                    yuv[yPos] = y
                }

                if (isEvenRow && (i and 1) == 0) {
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()

                    if (isPlanar) {
                        val uvRow = j shr 1
                        val uvCol = i shr 1
                        val halfStride = effectiveStride shr 1
                        val uPos = uPlaneBase + uvRow * halfStride + uvCol
                        val vPos = vPlaneBase + uvRow * halfStride + uvCol
                        if (uPos < totalFrameBytes) yuv[uPos] = u
                        if (vPos < totalFrameBytes) yuv[vPos] = v
                    } else {
                        val uvPos = uvBase + (j shr 1) * effectiveStride + i
                        if (uvPos + 1 < totalFrameBytes) {
                            yuv[uvPos] = u
                            yuv[uvPos + 1] = v
                        }
                    }
                }
            }
        }

        rawBuf.put(yuv, 0, totalFrameBytes)
        return totalFrameBytes
    }
}
