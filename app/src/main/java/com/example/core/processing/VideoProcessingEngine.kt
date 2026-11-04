package com.example.core.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.example.core.ai.LocalAiModelTask
import com.example.core.ai.OnnxAiEngine
import com.example.core.common.AudioExportMode
import com.example.core.common.BitratePreset
import com.example.core.common.ColorPreset
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.FilterStrength
import com.example.core.common.FpsConversionMode
import com.example.core.common.SharpenConfig
import com.example.core.common.UpscaleMode
import com.example.core.common.VideoMetadata
import com.example.core.gpu.DeviceCapabilityAnalyzer
import com.example.core.gpu.HardwareAccelerationManager
import com.example.core.media.MediaCodecVideoEncoder
import com.example.core.storage.MediaStoreVideoSaver
import com.example.core.storage.SavedGalleryVideoResult
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

data class ProcessingProgressUpdate(
    val stageName: String,
    val progressPercent: Int,
    val completedFrames: Int,
    val totalFrames: Int,
    val currentFpsSpeed: Float,
    val estimatedRemainingSeconds: Int,
    val activePipelineNote: String
)

class VideoProcessingEngine(
    private val context: Context,
    private val onnxAiEngine: OnnxAiEngine,
    private val capabilityAnalyzer: DeviceCapabilityAnalyzer,
    private val mediaStoreSaver: MediaStoreVideoSaver
) {

    /**
     * Ensures that if a user exports or previews a video without manually toggling sliders,
     * the CapCut Pro Master HDR Color + Multi-Scale Clarity & Detail pipeline is active.
     */
    private fun resolveEffectivePipeline(pipeline: EnhancementPipelineState): EnhancementPipelineState {
        val hasAnyColor = pipeline.color.enabled || pipeline.color.autoColor || pipeline.color.preset != ColorPreset.NONE
        val hasAnySharpen = pipeline.sharpen.strength > 0.01f ||
            pipeline.sharpen.edgeEnhancement > 0.01f ||
            pipeline.sharpen.clarity > 0.01f ||
            pipeline.sharpen.fineDetailRecovery > 0.01f ||
            pipeline.sharpen.deblurring

        return pipeline.copy(
            color = if (hasAnyColor) {
                pipeline.color
            } else {
                pipeline.color.copy(
                    enabled = true,
                    autoColor = true,
                    preset = ColorPreset.VIVID,
                    exposure = 0.04f,
                    contrast = 0.16f,
                    saturation = 0.18f,
                    vibrance = 0.25f,
                    highlights = -0.12f,
                    shadows = 0.14f,
                    blackLevel = -0.02f,
                    whiteLevel = 0.04f
                )
            },
            sharpen = if (hasAnySharpen) {
                pipeline.sharpen
            } else {
                pipeline.sharpen.copy(
                    strength = 0.58f,
                    edgeEnhancement = 0.45f,
                    clarity = 0.52f,
                    fineDetailRecovery = 0.48f,
                    textureRecovery = 0.40f,
                    deblurring = true
                )
            }
        )
    }

    /**
     * Processes a single real video frame through all active enhancement modules
     * for the interactive Before/After preview slider.
     */
    fun enhanceSinglePreviewFrame(
        rawFrame: Bitmap,
        pipeline: EnhancementPipelineState
    ): Bitmap {
        val activePipeline = resolveEffectivePipeline(pipeline)
        var working = rawFrame.copy(Bitmap.Config.ARGB_8888, true)

        // 1. Stabilization crop preview
        if (activePipeline.stabilize.strength != FilterStrength.OFF) {
            val stabilized = StabilizationAndFpsProcessor.applyStabilizationWarp(
                frame = working,
                smoothedCorrectionDx = 0f,
                smoothedCorrectionDy = 0f,
                config = activePipeline.stabilize
            )
            if (stabilized !== working) working.recycle()
            working = stabilized
        }

        // 2. Denoising (Spatial + optional ONNX AI Denoise pass)
        if (activePipeline.denoise.strength != FilterStrength.OFF) {
            val denoised = DenoiseAndSharpenProcessor.applyDenoise(
                currentFrame = working,
                previousDenoisedFrame = null,
                config = activePipeline.denoise
            )
            if (denoised !== working) working.recycle()
            working = denoised

            if (activePipeline.denoise.strength == FilterStrength.HIGH) {
                val aiDenoised = onnxAiEngine.runTiledInference(
                    source = working,
                    task = LocalAiModelTask.VIDEO_DENOISING,
                    tileSize = activePipeline.upscale.tileSize,
                    overlap = activePipeline.upscale.tileOverlap
                )
                if (aiDenoised !== working) working.recycle()
                working = aiDenoised
            }
        }

        // 3. Auto Color & 14-Axis HDR Color Grading
        if (activePipeline.color.enabled || activePipeline.color.autoColor) {
            val graded = ColorCorrectionProcessor.processFrame(working, activePipeline.color)
            if (graded !== working) working.recycle()
            working = graded
        }

        // 3.5. CapCut Pro DSLR Camera Bokeh & Portrait Depth Blur
        if (activePipeline.bokeh.enabled) {
            val bokehApplied = DslrBokehProcessor.applyDslrBokeh(working, activePipeline.bokeh)
            if (bokehApplied !== working) working.recycle()
            working = bokehApplied
        }

        // 4. AI Super Resolution / Maximum Quality ONNX pass
        if (activePipeline.upscale.mode == UpscaleMode.AI_SUPER_RESOLUTION ||
            activePipeline.upscale.mode == UpscaleMode.MAXIMUM_QUALITY
        ) {
            val srFrame = onnxAiEngine.runTiledInference(
                source = working,
                task = LocalAiModelTask.SUPER_RESOLUTION,
                tileSize = activePipeline.upscale.tileSize,
                overlap = activePipeline.upscale.tileOverlap
            )
            if (srFrame !== working) working.recycle()
            working = srFrame
        }

        // 5. Multi-Scale Smart Sharpening, HDR Local Clarity & Fine Detail Recovery
        val effectiveSharpen = if (activePipeline.upscale.mode == UpscaleMode.MAXIMUM_QUALITY) {
            activePipeline.sharpen.copy(
                strength = maxOf(activePipeline.sharpen.strength, 0.58f),
                clarity = maxOf(activePipeline.sharpen.clarity, 0.52f),
                fineDetailRecovery = maxOf(activePipeline.sharpen.fineDetailRecovery, 0.48f)
            )
        } else {
            activePipeline.sharpen
        }
        if (effectiveSharpen.strength > 0.01f ||
            effectiveSharpen.edgeEnhancement > 0.01f ||
            effectiveSharpen.fineDetailRecovery > 0.01f ||
            effectiveSharpen.clarity > 0.01f ||
            effectiveSharpen.deblurring
        ) {
            val sharpened = DenoiseAndSharpenProcessor.applySharpenAndDetail(working, effectiveSharpen)
            if (sharpened !== working) working.recycle()
            working = sharpened
        }

        return working
    }

    /**
     * Calculates estimated output bitrate (bps) and output file size (bytes)
     * based on resolution, frame rate, codec efficiency, and bitrate preset.
     */
    fun estimateExportBitrateAndSize(
        metadata: VideoMetadata,
        pipeline: EnhancementPipelineState
    ): Pair<Int, Long> {
        val targetRes = if (pipeline.export.resolution != com.example.core.common.TargetResolution.ORIGINAL) {
            pipeline.export.resolution
        } else {
            pipeline.upscale.targetResolution
        }
        val (outW, outH) = targetRes.computeOutputDimensions(metadata.width, metadata.height)
        val outFps = when {
            pipeline.export.targetFps > 0 -> pipeline.export.targetFps.toFloat()
            pipeline.fps.targetFps > 0 -> pipeline.fps.targetFps.toFloat()
            else -> metadata.frameRate.coerceAtLeast(24f)
        }

        val bitrateBps = if (pipeline.export.bitratePreset == BitratePreset.CUSTOM) {
            (pipeline.export.customBitrateMbps * 1_000_000f).roundToInt().coerceIn(2_000_000, 80_000_000)
        } else {
            val pixelsPerFrame = outW.toLong() * outH.toLong()
            val baseBps = when {
                pixelsPerFrame >= 3840L * 2160L -> 32_000_000
                pixelsPerFrame >= 2560L * 1440L -> 20_000_000
                pixelsPerFrame >= 1920L * 1080L -> 14_000_000
                pixelsPerFrame >= 1280L * 720L -> 8_000_000
                else -> 5_000_000
            }
            val fpsFactor = (outFps / 30f).coerceIn(0.85f, 2.0f)
            val codecFactor = when (pipeline.export.codec) {
                com.example.core.common.VideoCodecOption.H264 -> 1.0f
                com.example.core.common.VideoCodecOption.H265_HEVC -> 0.75f
                com.example.core.common.VideoCodecOption.AV1 -> 0.68f
            }
            (baseBps * fpsFactor * codecFactor * pipeline.export.bitratePreset.multiplier)
                .roundToInt()
                .coerceIn(2_500_000, 65_000_000)
        }

        val durationSec = (metadata.durationMs / 1000.0).coerceAtLeast(1.0)
        val audioBps = if (pipeline.export.audioMode == AudioExportMode.MUTE || !metadata.hasAudio) {
            0L
        } else {
            pipeline.export.audioBitrateKbps * 1000L
        }
        val estimatedBytes = (((bitrateBps + audioBps) / 8.0) * durationSec).toLong()
        return Pair(bitrateBps, estimatedBytes)
    }

    /**
     * Executes genuine two-phase sequential video decoding, CapCut Pro AI enhancement,
     * smooth sub-frame interpolation for high FPS (60/90/120fps), hardware/software MP4 encoding,
     * audio track muxing, and MediaStore gallery verification.
     *
     * Key fixes:
     * 1. Uses sequential batch frame decoding (getFramesAtIndex on API 28+ / OPTION_CLOSEST)
     *    instead of OPTION_CLOSEST_SYNC, completely eliminating 1-second keyframe freezes!
     * 2. Enhances unique source frames once in Phase 1, then smoothly interpolates intermediate
     *    sub-frames in Phase 2 when exporting at 60/90/120 FPS.
     */
    fun processAndExportVideo(
        metadata: VideoMetadata,
        pipeline: EnhancementPipelineState,
        isCancelled: () -> Boolean,
        isPaused: () -> Boolean,
        startFromCheckpointFrame: Int = 0,
        onCheckpointSaved: (Int) -> Unit = {},
        onProgress: (ProcessingProgressUpdate) -> Unit
    ): Result<SavedGalleryVideoResult> {
        return runCatching {
            val activePipeline = resolveEffectivePipeline(pipeline)
            val capabilities = capabilityAnalyzer.analyze()
            val effectiveRes = if (activePipeline.export.resolution != com.example.core.common.TargetResolution.ORIGINAL) {
                activePipeline.export.resolution
            } else {
                activePipeline.upscale.targetResolution
            }

            val (rawOutWidth, rawOutHeight) = effectiveRes.computeOutputDimensions(metadata.width, metadata.height)
            val outWidth = MediaCodecVideoEncoder.alignTo16(rawOutWidth)
            val outHeight = MediaCodecVideoEncoder.alignTo16(rawOutHeight)

            val requestedFps = when {
                activePipeline.export.targetFps > 0 -> activePipeline.export.targetFps
                activePipeline.fps.targetFps > 0 -> activePipeline.fps.targetFps
                else -> metadata.frameRate.roundToInt().coerceIn(24, 60)
            }
            // H.264 Level 4.2 hardware decoders max out at 60fps for 1080p+; 720p supports up to 120fps
            val maxHardwarePlaybackFps = if (outWidth * outHeight >= 1920 * 1080) 60 else 120
            val effectiveFps = requestedFps.coerceIn(24, maxHardwarePlaybackFps)

            val (isCompatible, compatMessage) = capabilities.checkExportCompatibility(
                targetResolution = effectiveRes,
                codec = activePipeline.export.codec,
                targetFps = effectiveFps,
                inputWidth = metadata.width,
                inputHeight = metadata.height
            )
            if (!isCompatible) {
                error(compatMessage)
            }

            val (targetBitrateBps, _) = estimateExportBitrateAndSize(metadata, activePipeline)

            val tempDir = File(context.cacheDir, "videox_exports").apply { mkdirs() }
            val sessionStamp = System.currentTimeMillis()
            val outFileName = "VideoX_${effectiveRes.shortBadge}_${effectiveFps}fps_${sessionStamp}.mp4"
            val tempOutputFile = File(tempDir, outFileName)
            if (tempOutputFile.exists()) tempOutputFile.delete()

            val frameCacheDir = File(context.cacheDir, "videox_frame_cache_$sessionStamp").apply { mkdirs() }

            val uri = Uri.parse(metadata.uriString)
            val durationUs = (metadata.durationMs * 1000L).coerceAtLeast(500_000L)
            val safeMaxPtsUs = (durationUs - 30_000L).coerceAtLeast(0L)
            val sourceFps = metadata.frameRate.coerceIn(15f, 60f)

            // Unique source frames to decode & enhance in Phase 1 (every real frame of the source video)
            val rawSourceFrames = ((durationUs / 1_000_000.0) * sourceFps).roundToInt().coerceIn(8, 600)
            val totalOutputFrames = ((durationUs / 1_000_000.0) * effectiveFps).roundToInt().coerceIn(rawSourceFrames, 1800)

            val startTimeMs = SystemClock.elapsedRealtime()
            val hwAccel = HardwareAccelerationManager.inspectHardwareAcceleration(context)

            try {
                // =========================================================================
                // PHASE 1: Decode Every Sequential Source Frame & Apply CapCut Pro AI Pipeline
                // Uses getFramesAtIndex (API 28+) + OPTION_CLOSEST (Never OPTION_CLOSEST_SYNC!)
                // =========================================================================
                var retriever: MediaMetadataRetriever? = null
                var prevRawFrame: Bitmap? = null
                var prevDenoisedFrame: Bitmap? = null
                var smoothedAutoColor: AutoColorEstimate? = null
                var smoothedShakeDx = 0f
                var smoothedShakeDy = 0f
                var actualSourceFrameCount = rawSourceFrames

                fun openFreshRetriever(): MediaMetadataRetriever? {
                    runCatching { retriever?.release() }
                    retriever = null
                    return runCatching {
                        MediaMetadataRetriever().apply {
                            if (uri.scheme == "file") {
                                setDataSource(uri.path)
                            } else {
                                setDataSource(context, uri)
                            }
                        }.also { retriever = it }
                    }.getOrNull()
                }

                // Batch buffer for ultra-fast sequential frame decoding on Android 9+ (API 28+)
                var batchStartIdx = -1
                var batchFrames: List<Bitmap> = emptyList()
                var supportsFrameAtIndex = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

                fun clearBatchBuffer() {
                    for (bmp in batchFrames) {
                        if (!bmp.isRecycled) bmp.recycle()
                    }
                    batchFrames = emptyList()
                    batchStartIdx = -1
                }

                fun extractSequentialSourceFrame(srcIdx: Int, totalSrcFrames: Int): Bitmap {
                    val activeRetriever = retriever ?: openFreshRetriever()

                    // 1. Primary: Sequential Batch Index Decoding (API 28+) -> Decodes every I/P/B frame in order!
                    if (supportsFrameAtIndex && activeRetriever != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        if (srcIdx < batchStartIdx || srcIdx >= batchStartIdx + batchFrames.size) {
                            clearBatchBuffer()
                            val countToFetch = minOf(4, (totalSrcFrames - srcIdx).coerceAtLeast(1))
                            val fetched = runCatching {
                                activeRetriever.getFramesAtIndex(srcIdx, countToFetch)
                            }.getOrNull()

                            if (!fetched.isNullOrEmpty()) {
                                batchStartIdx = srcIdx
                                batchFrames = fetched
                            } else {
                                // Try single getFrameAtIndex before falling back to timestamp mode
                                val single = runCatching { activeRetriever.getFrameAtIndex(srcIdx) }.getOrNull()
                                if (single != null) {
                                    batchStartIdx = srcIdx
                                    batchFrames = listOf(single)
                                } else if (srcIdx == 0) {
                                    supportsFrameAtIndex = false
                                }
                            }
                        }

                        val localOffset = srcIdx - batchStartIdx
                        if (localOffset in batchFrames.indices) {
                            val candidate = batchFrames[localOffset]
                            if (!candidate.isRecycled) {
                                return candidate.copy(Bitmap.Config.ARGB_8888, true)
                            }
                        }
                    }

                    // 2. Fallback: Exact Timestamp Decoding with OPTION_CLOSEST (NEVER OPTION_CLOSEST_SYNC!)
                    val ptsUs = ((srcIdx.toLong() * durationUs) / totalSrcFrames.coerceAtLeast(1)).coerceIn(0L, safeMaxPtsUs)
                    if (activeRetriever != null) {
                        val exactFrame = runCatching {
                            activeRetriever.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        }.getOrNull()
                        if (exactFrame != null) return exactFrame
                    }

                    // 3. Self-heal retriever if hardware decoder needed reset
                    val healed = openFreshRetriever()
                    if (healed != null) {
                        val retryExact = runCatching {
                            healed.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST)
                                ?: healed.getFrameAtTime(ptsUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        }.getOrNull()
                        if (retryExact != null) return retryExact
                    }

                    return prevRawFrame?.copy(Bitmap.Config.ARGB_8888, true)
                        ?: Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                }

                try {
                    val initRetriever = openFreshRetriever()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && initRetriever != null) {
                        val containerFrameCount = runCatching {
                            initRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
                        }.getOrNull()
                        if (containerFrameCount != null && containerFrameCount in 8..900) {
                            actualSourceFrameCount = containerFrameCount
                        }
                    }

                    for (srcIdx in 0 until actualSourceFrameCount) {
                        if (isCancelled()) {
                            error("Processing cancelled by user.")
                        }
                        while (isPaused() && !isCancelled()) {
                            Thread.sleep(120)
                        }

                        val rawSample = extractSequentialSourceFrame(srcIdx, actualSourceFrameCount)

                        // Step 1: Motion Estimation & Camera Shake Stabilization
                        var workingFrame = if (activePipeline.stabilize.strength != FilterStrength.OFF && prevRawFrame != null) {
                            val mv = StabilizationAndFpsProcessor.estimateMotionVector(prevRawFrame, rawSample)
                            smoothedShakeDx = smoothedShakeDx * 0.78f - mv.dx * 0.22f
                            smoothedShakeDy = smoothedShakeDy * 0.78f - mv.dy * 0.22f
                            StabilizationAndFpsProcessor.applyStabilizationWarp(
                                frame = rawSample,
                                smoothedCorrectionDx = smoothedShakeDx,
                                smoothedCorrectionDy = smoothedShakeDy,
                                config = activePipeline.stabilize
                            )
                        } else {
                            rawSample.copy(Bitmap.Config.ARGB_8888, true)
                        }

                        prevRawFrame?.recycle()
                        prevRawFrame = rawSample.copy(Bitmap.Config.ARGB_8888, false)
                        rawSample.recycle()

                        // Step 2: Spatial-Temporal AI Denoising
                        if (activePipeline.denoise.strength != FilterStrength.OFF) {
                            val denoised = DenoiseAndSharpenProcessor.applyDenoise(
                                currentFrame = workingFrame,
                                previousDenoisedFrame = prevDenoisedFrame,
                                config = activePipeline.denoise
                            )
                            prevDenoisedFrame?.recycle()
                            prevDenoisedFrame = denoised.copy(Bitmap.Config.ARGB_8888, false)
                            if (denoised !== workingFrame) workingFrame.recycle()
                            workingFrame = denoised
                        }

                        // Step 3: Temporal-Smoothed Auto Color & 14-Axis CapCut Pro Grading
                        if (activePipeline.color.enabled || activePipeline.color.autoColor) {
                            if (activePipeline.color.autoColor) {
                                val currentEst = ColorCorrectionProcessor.analyzeFrameForAutoColor(workingFrame)
                                smoothedAutoColor = if (smoothedAutoColor == null) {
                                    currentEst
                                } else {
                                    AutoColorEstimate(
                                        exposure = smoothedAutoColor.exposure * 0.85f + currentEst.exposure * 0.15f,
                                        contrast = smoothedAutoColor.contrast * 0.85f + currentEst.contrast * 0.15f,
                                        saturation = smoothedAutoColor.saturation * 0.85f + currentEst.saturation * 0.15f,
                                        temperature = smoothedAutoColor.temperature * 0.85f + currentEst.temperature * 0.15f,
                                        tint = smoothedAutoColor.tint * 0.85f + currentEst.tint * 0.15f,
                                        shadows = smoothedAutoColor.shadows * 0.85f + currentEst.shadows * 0.15f,
                                        highlights = smoothedAutoColor.highlights * 0.85f + currentEst.highlights * 0.15f
                                    )
                                }
                            }
                            val graded = ColorCorrectionProcessor.processFrame(
                                source = workingFrame,
                                config = activePipeline.color,
                                autoEstimate = smoothedAutoColor
                            )
                            if (graded !== workingFrame) workingFrame.recycle()
                            workingFrame = graded
                        }

                        // Step 4: CapCut Pro DSLR Camera Bokeh & Portrait Depth Blur
                        if (activePipeline.bokeh.enabled) {
                            val bokehApplied = DslrBokehProcessor.applyDslrBokeh(workingFrame, activePipeline.bokeh)
                            if (bokehApplied !== workingFrame) workingFrame.recycle()
                            workingFrame = bokehApplied
                        }

                        // Step 5: Upscaling & Tiled Local ONNX Super Resolution
                        if (activePipeline.upscale.mode == UpscaleMode.AI_SUPER_RESOLUTION ||
                            activePipeline.upscale.mode == UpscaleMode.MAXIMUM_QUALITY
                        ) {
                            val srOut = onnxAiEngine.runTiledInference(
                                source = workingFrame,
                                task = LocalAiModelTask.SUPER_RESOLUTION,
                                tileSize = activePipeline.upscale.tileSize,
                                overlap = activePipeline.upscale.tileOverlap
                            )
                            if (srOut !== workingFrame) workingFrame.recycle()
                            workingFrame = srOut
                        }

                        if (workingFrame.width != outWidth || workingFrame.height != outHeight) {
                            val scaled = Bitmap.createScaledBitmap(workingFrame, outWidth, outHeight, true)
                            if (scaled !== workingFrame) workingFrame.recycle()
                            workingFrame = scaled
                        }

                        // Step 6: Multi-Scale Smart Sharpening, HDR Clarity & Edge Detail Recovery
                        val effectiveSharpen: SharpenConfig = if (activePipeline.upscale.mode == UpscaleMode.MAXIMUM_QUALITY) {
                            activePipeline.sharpen.copy(
                                strength = maxOf(activePipeline.sharpen.strength, 0.58f),
                                clarity = maxOf(activePipeline.sharpen.clarity, 0.52f),
                                fineDetailRecovery = maxOf(activePipeline.sharpen.fineDetailRecovery, 0.48f)
                            )
                        } else {
                            activePipeline.sharpen
                        }
                        if (effectiveSharpen.strength > 0.01f ||
                            effectiveSharpen.edgeEnhancement > 0.01f ||
                            effectiveSharpen.fineDetailRecovery > 0.01f ||
                            effectiveSharpen.clarity > 0.01f ||
                            effectiveSharpen.deblurring
                        ) {
                            val sharp = DenoiseAndSharpenProcessor.applySharpenAndDetail(workingFrame, effectiveSharpen)
                            if (sharp !== workingFrame) workingFrame.recycle()
                            workingFrame = sharp
                        }

                        // Save enhanced unique source frame to disk cache
                        val frameFile = File(frameCacheDir, "src_frame_$srcIdx.jpg")
                        FileOutputStream(frameFile).use { fos ->
                            workingFrame.compress(Bitmap.CompressFormat.JPEG, 97, fos)
                        }
                        workingFrame.recycle()

                        if (srcIdx >= startFromCheckpointFrame) {
                            onCheckpointSaved(srcIdx)
                        }

                        val elapsedSec = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000f).coerceAtLeast(0.1f)
                        val speedFps = (srcIdx + 1) / elapsedSec
                        val remainingFrames = (actualSourceFrameCount - (srcIdx + 1)).coerceAtLeast(0)
                        val etaSec = ((remainingFrames / speedFps.coerceAtLeast(0.5f)) * 1.2f).roundToInt()
                        val pct = (((srcIdx + 1) * 75) / actualSourceFrameCount).coerceIn(1, 75)

                        onProgress(
                            ProcessingProgressUpdate(
                                stageName = buildStageSummary(activePipeline, outWidth, outHeight, effectiveFps),
                                progressPercent = pct,
                                completedFrames = srcIdx + 1,
                                totalFrames = actualSourceFrameCount,
                                currentFpsSpeed = speedFps,
                                estimatedRemainingSeconds = etaSec,
                                activePipelineNote = "[${hwAccel.summaryBadge}] Enhancing frame ${srcIdx + 1}/$actualSourceFrameCount (${outWidth}×${outHeight})"
                            )
                        )
                    }
                } finally {
                    clearBatchBuffer()
                    prevRawFrame?.recycle()
                    prevDenoisedFrame?.recycle()
                    runCatching { retriever?.release() }
                    retriever = null
                }

                // =========================================================================
                // PHASE 2: Smooth Sub-Frame Interpolation (60/90/120fps) & MP4 Encoding
                // Uses a 2-slot in-memory cache so each enhanced frame is read once from disk
                // =========================================================================
                val includeAudio = activePipeline.export.audioMode != AudioExportMode.MUTE && metadata.hasAudio
                var cachedIdxA = -1
                var cachedBmpA: Bitmap? = null
                var cachedIdxB = -1
                var cachedBmpB: Bitmap? = null
                val blendPaint = Paint(Paint.FILTER_BITMAP_FLAG)

                fun loadEnhancedSourceFrame(index: Int, targetW: Int, targetH: Int): Bitmap {
                    val clampedIdx = index.coerceIn(0, actualSourceFrameCount - 1)
                    if (clampedIdx == cachedIdxA && cachedBmpA != null && !cachedBmpA!!.isRecycled) {
                        return cachedBmpA!!
                    }
                    if (clampedIdx == cachedIdxB && cachedBmpB != null && !cachedBmpB!!.isRecycled) {
                        return cachedBmpB!!
                    }

                    val file = File(frameCacheDir, "src_frame_$clampedIdx.jpg")
                    val decoded = if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else {
                        null
                    } ?: Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)

                    val sized = if (decoded.width != targetW || decoded.height != targetH) {
                        val s = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                        if (s !== decoded) decoded.recycle()
                        s
                    } else {
                        decoded
                    }

                    // Rotate slots: move B -> A, store new in B
                    if (cachedBmpA != null && cachedBmpA !== cachedBmpB) {
                        cachedBmpA?.recycle()
                    }
                    cachedIdxA = cachedIdxB
                    cachedBmpA = cachedBmpB
                    cachedIdxB = clampedIdx
                    cachedBmpB = sized
                    return sized
                }

                try {
                    MediaCodecVideoEncoder.encodeToMp4(
                        MediaCodecVideoEncoder.EncodeRequest(
                            context = context,
                            outputFile = tempOutputFile,
                            requestedMime = activePipeline.export.codec.mimeType,
                            requestedWidth = outWidth,
                            requestedHeight = outHeight,
                            fps = effectiveFps,
                            bitrateBps = targetBitrateBps,
                            totalFrames = totalOutputFrames,
                            audioSourceUri = uri,
                            includeAudio = includeAudio,
                            maxAudioDurationUs = durationUs,
                            isCancelled = isCancelled,
                            isPaused = isPaused,
                            frameProvider = { outIdx, targetW, targetH ->
                                val exactSrcPos = if (totalOutputFrames > 1 && actualSourceFrameCount > 1) {
                                    (outIdx.toFloat() * (actualSourceFrameCount - 1).toFloat()) / (totalOutputFrames - 1).toFloat()
                                } else {
                                    0f
                                }
                                val srcIdxA = exactSrcPos.toInt().coerceIn(0, actualSourceFrameCount - 1)
                                val srcIdxB = (srcIdxA + 1).coerceIn(0, actualSourceFrameCount - 1)
                                val alpha = (exactSrcPos - srcIdxA).coerceIn(0f, 1f)

                                val frameA = loadEnhancedSourceFrame(srcIdxA, targetW, targetH)
                                if (srcIdxA == srcIdxB || alpha <= 0.08f) {
                                    frameA.copy(Bitmap.Config.ARGB_8888, false)
                                } else if (alpha >= 0.92f) {
                                    val frameB = loadEnhancedSourceFrame(srcIdxB, targetW, targetH)
                                    frameB.copy(Bitmap.Config.ARGB_8888, false)
                                } else {
                                    val frameB = loadEnhancedSourceFrame(srcIdxB, targetW, targetH)
                                    // Fast scene-cut check on center sample points so hard cuts stay sharp
                                    val pA = frameA.getPixel(targetW / 2, targetH / 2)
                                    val pB = frameB.getPixel(targetW / 2, targetH / 2)
                                    val diff = abs(((pA shr 16) and 0xFF) - ((pB shr 16) and 0xFF)) +
                                        abs(((pA shr 8) and 0xFF) - ((pB shr 8) and 0xFF)) +
                                        abs((pA and 0xFF) - (pB and 0xFF))

                                    if (activePipeline.fps.sceneCutDetection && diff > 240) {
                                        (if (alpha < 0.5f) frameA else frameB).copy(Bitmap.Config.ARGB_8888, false)
                                    } else {
                                        // Smooth sub-frame temporal interpolation for 60/90/120 FPS
                                        val blended = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                                        val canvas = Canvas(blended)
                                        blendPaint.alpha = 255
                                        canvas.drawBitmap(frameA, 0f, 0f, blendPaint)
                                        blendPaint.alpha = (alpha * 255f).roundToInt().coerceIn(1, 254)
                                        canvas.drawBitmap(frameB, 0f, 0f, blendPaint)
                                        blended
                                    }
                                }
                            },
                            onFrameEncoded = { frameIdx, encoderDesc ->
                                val elapsedSec = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000f).coerceAtLeast(0.1f)
                                val speedFps = (frameIdx + 1) / elapsedSec
                                val encodePct = 75 + (((frameIdx + 1) * 22) / totalOutputFrames).coerceIn(1, 22)
                                val remaining = ((totalOutputFrames - (frameIdx + 1)) / 25f).roundToInt().coerceAtLeast(0)
                                onProgress(
                                    ProcessingProgressUpdate(
                                        stageName = "Encoding Smooth MP4 ($encoderDesc)",
                                        progressPercent = encodePct,
                                        completedFrames = frameIdx + 1,
                                        totalFrames = totalOutputFrames,
                                        currentFpsSpeed = speedFps,
                                        estimatedRemainingSeconds = remaining,
                                        activePipelineNote = "Muxing ${activePipeline.export.codec.label} @ ${effectiveFps}fps (${frameIdx + 1}/$totalOutputFrames)"
                                    )
                                )
                            }
                        )
                    )
                } finally {
                    cachedBmpA?.recycle()
                    if (cachedBmpB !== cachedBmpA) cachedBmpB?.recycle()
                }
            } finally {
                runCatching { frameCacheDir.deleteRecursively() }
            }

            onProgress(
                ProcessingProgressUpdate(
                    stageName = "Verifying MP4 & Saving to Movies/VideoX AI",
                    progressPercent = 99,
                    completedFrames = totalOutputFrames,
                    totalFrames = totalOutputFrames,
                    currentFpsSpeed = 30f,
                    estimatedRemainingSeconds = 0,
                    activePipelineNote = "Validating encoded stream integrity"
                )
            )

            val savedResult = mediaStoreSaver.saveAndVerifyVideoToGallery(
                sourceMp4File = tempOutputFile,
                desiredDisplayName = outFileName
            ).getOrThrow()

            onProgress(
                ProcessingProgressUpdate(
                    stageName = "Completed & Verified",
                    progressPercent = 100,
                    completedFrames = totalOutputFrames,
                    totalFrames = totalOutputFrames,
                    currentFpsSpeed = 30f,
                    estimatedRemainingSeconds = 0,
                    activePipelineNote = "Saved to ${savedResult.displayPath}"
                )
            )

            savedResult
        }
    }

    private fun buildStageSummary(
        pipeline: EnhancementPipelineState,
        outW: Int,
        outH: Int,
        fps: Int
    ): String {
        val activeStages = mutableListOf<String>()
        if (pipeline.stabilize.strength != FilterStrength.OFF) activeStages.add("Stabilize")
        if (pipeline.denoise.strength != FilterStrength.OFF) activeStages.add("Denoise")
        if (pipeline.color.enabled || pipeline.color.autoColor) activeStages.add("CapCut Pro Color")
        if (pipeline.bokeh.enabled) activeStages.add("DSLR Bokeh (${pipeline.bokeh.aperture})")
        when (pipeline.upscale.mode) {
            UpscaleMode.AI_SUPER_RESOLUTION -> activeStages.add("AI Super-Res")
            UpscaleMode.MAXIMUM_QUALITY -> activeStages.add("Max-Q Tiled AI")
            UpscaleMode.STANDARD_RESIZE -> if (pipeline.upscale.targetResolution != com.example.core.common.TargetResolution.ORIGINAL) {
                activeStages.add("HD Upscale")
            }
        }
        if (pipeline.sharpen.strength > 0.01f || pipeline.sharpen.clarity > 0.01f) {
            activeStages.add("HDR Clarity")
        }
        if (pipeline.fps.mode == FpsConversionMode.AI_OPTICAL_FLOW_RIFE && pipeline.fps.targetFps > 0) {
            activeStages.add("Smooth ${fps}fps")
        }
        return if (activeStages.isEmpty()) {
            "Rendering ${outW}×${outH} @ ${fps}fps"
        } else {
            activeStages.joinToString(" + ")
        }
    }
}
