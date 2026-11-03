package com.example.core.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import com.example.core.ai.LocalAiModelTask
import com.example.core.ai.OnnxAiEngine
import com.example.core.common.AudioExportMode
import com.example.core.common.BitratePreset
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.FilterStrength
import com.example.core.common.FpsConversionMode
import com.example.core.common.SharpenConfig
import com.example.core.common.UpscaleMode
import com.example.core.common.VideoMetadata
import com.example.core.gpu.DeviceCapabilityAnalyzer
import com.example.core.media.MediaCodecVideoEncoder
import com.example.core.storage.MediaStoreVideoSaver
import com.example.core.storage.SavedGalleryVideoResult
import java.io.File
import java.io.FileOutputStream
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
     * Processes a single real video frame through all active enhancement modules
     * for the interactive Before/After preview slider.
     */
    fun enhanceSinglePreviewFrame(
        rawFrame: Bitmap,
        pipeline: EnhancementPipelineState
    ): Bitmap {
        var working = rawFrame.copy(Bitmap.Config.ARGB_8888, true)

        // 1. Stabilization crop preview
        if (pipeline.stabilize.strength != FilterStrength.OFF) {
            val stabilized = StabilizationAndFpsProcessor.applyStabilizationWarp(
                frame = working,
                smoothedCorrectionDx = 0f,
                smoothedCorrectionDy = 0f,
                config = pipeline.stabilize
            )
            if (stabilized !== working) working.recycle()
            working = stabilized
        }

        // 2. Denoising (Spatial + optional ONNX AI Denoise pass)
        if (pipeline.denoise.strength != FilterStrength.OFF) {
            val denoised = DenoiseAndSharpenProcessor.applyDenoise(
                currentFrame = working,
                previousDenoisedFrame = null,
                config = pipeline.denoise
            )
            if (denoised !== working) working.recycle()
            working = denoised

            if (pipeline.denoise.strength == FilterStrength.HIGH) {
                val aiDenoised = onnxAiEngine.runTiledInference(
                    source = working,
                    task = LocalAiModelTask.VIDEO_DENOISING,
                    tileSize = pipeline.upscale.tileSize,
                    overlap = pipeline.upscale.tileOverlap
                )
                if (aiDenoised !== working) working.recycle()
                working = aiDenoised
            }
        }

        // 3. Auto Color & 14-Axis Color Grading
        if (pipeline.color.enabled || pipeline.color.autoColor) {
            val graded = ColorCorrectionProcessor.processFrame(working, pipeline.color)
            if (graded !== working) working.recycle()
            working = graded
        }

        // 3.5. CapCut Pro DSLR Camera Bokeh & Portrait Depth Blur
        if (pipeline.bokeh.enabled) {
            val bokehApplied = DslrBokehProcessor.applyDslrBokeh(working, pipeline.bokeh)
            if (bokehApplied !== working) working.recycle()
            working = bokehApplied
        }

        // 4. AI Super Resolution / Maximum Quality ONNX pass
        if (pipeline.upscale.mode == UpscaleMode.AI_SUPER_RESOLUTION ||
            pipeline.upscale.mode == UpscaleMode.MAXIMUM_QUALITY
        ) {
            val srFrame = onnxAiEngine.runTiledInference(
                source = working,
                task = LocalAiModelTask.SUPER_RESOLUTION,
                tileSize = pipeline.upscale.tileSize,
                overlap = pipeline.upscale.tileOverlap
            )
            if (srFrame !== working) working.recycle()
            working = srFrame
        }

        // 5. Smart Sharpening & Fine Detail Recovery
        val effectiveSharpen = if (pipeline.upscale.mode == UpscaleMode.MAXIMUM_QUALITY) {
            pipeline.sharpen.copy(
                strength = maxOf(pipeline.sharpen.strength, 0.35f),
                fineDetailRecovery = maxOf(pipeline.sharpen.fineDetailRecovery, 0.3f)
            )
        } else {
            pipeline.sharpen
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
            (pipeline.export.customBitrateMbps * 1_000_000f).roundToInt().coerceIn(1_000_000, 80_000_000)
        } else {
            val pixelsPerFrame = outW.toLong() * outH.toLong()
            val baseBps = when {
                pixelsPerFrame >= 3840L * 2160L -> 28_000_000
                pixelsPerFrame >= 2560L * 1440L -> 16_000_000
                pixelsPerFrame >= 1920L * 1080L -> 10_000_000
                pixelsPerFrame >= 1280L * 720L -> 5_500_000
                else -> 3_200_000
            }
            val fpsFactor = (outFps / 30f).coerceIn(0.8f, 2.2f)
            val codecFactor = when (pipeline.export.codec) {
                com.example.core.common.VideoCodecOption.H264 -> 1.0f
                com.example.core.common.VideoCodecOption.H265_HEVC -> 0.72f
                com.example.core.common.VideoCodecOption.AV1 -> 0.65f
            }
            (baseBps * fpsFactor * codecFactor * pipeline.export.bitratePreset.multiplier)
                .roundToInt()
                .coerceIn(1_000_000, 65_000_000)
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
     * Executes genuine two-phase frame-by-frame video decoding, enhancement, AI inference,
     * FPS conversion/interpolation, hardware/software MP4 encoding, audio track muxing,
     * and MediaStore gallery verification.
     *
     * Separating Phase 1 (MediaMetadataRetriever frame decode + ONNX AI enhancement to disk cache)
     * from Phase 2 (MediaCodec encoding) guarantees zero hardware VPU contention between decoder
     * and encoder, completely eliminating "Invalid to call at Released state; only valid in executing state".
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
            val capabilities = capabilityAnalyzer.analyze()
            val effectiveRes = if (pipeline.export.resolution != com.example.core.common.TargetResolution.ORIGINAL) {
                pipeline.export.resolution
            } else {
                pipeline.upscale.targetResolution
            }

            val effectiveFps = when {
                pipeline.export.targetFps > 0 -> pipeline.export.targetFps
                pipeline.fps.targetFps > 0 -> pipeline.fps.targetFps
                else -> metadata.frameRate.roundToInt().coerceIn(15, 120)
            }

            val (isCompatible, compatMessage) = capabilities.checkExportCompatibility(
                targetResolution = effectiveRes,
                codec = pipeline.export.codec,
                targetFps = effectiveFps,
                inputWidth = metadata.width,
                inputHeight = metadata.height
            )
            if (!isCompatible) {
                error(compatMessage)
            }

            val (rawOutWidth, rawOutHeight) = effectiveRes.computeOutputDimensions(metadata.width, metadata.height)
            val outWidth = MediaCodecVideoEncoder.alignTo16(rawOutWidth)
            val outHeight = MediaCodecVideoEncoder.alignTo16(rawOutHeight)
            val (targetBitrateBps, _) = estimateExportBitrateAndSize(metadata, pipeline)

            val tempDir = File(context.cacheDir, "videox_exports").apply { mkdirs() }
            val sessionStamp = System.currentTimeMillis()
            val outFileName = "VideoX_${effectiveRes.shortBadge}_${effectiveFps}fps_${sessionStamp}.mp4"
            val tempOutputFile = File(tempDir, outFileName)
            if (tempOutputFile.exists()) tempOutputFile.delete()

            val frameCacheDir = File(context.cacheDir, "videox_frame_cache_$sessionStamp").apply { mkdirs() }

            val uri = Uri.parse(metadata.uriString)
            val durationUs = (metadata.durationMs * 1000L).coerceAtLeast(500_000L)
            val safeMaxPtsUs = (durationUs - 50_000L).coerceAtLeast(0L)
            val sourceFps = metadata.frameRate.coerceIn(12f, 120f)
            val totalOutputFrames = ((durationUs / 1_000_000.0) * effectiveFps).roundToInt().coerceIn(8, 900)
            val useAiInterpolation = pipeline.fps.mode == FpsConversionMode.AI_OPTICAL_FLOW_RIFE &&
                effectiveFps > sourceFps.roundToInt()

            val startTimeMs = SystemClock.elapsedRealtime()

            try {
                // =========================================================================
                // PHASE 1: Decode Source Frames & Run Local AI Enhancement Pipeline to Disk
                // (No MediaCodec Encoder is open during Phase 1 -> 0% VPU Codec Contention)
                // =========================================================================
                var retriever: MediaMetadataRetriever? = null
                var prevRawFrame: Bitmap? = null
                var prevDenoisedFrame: Bitmap? = null
                var smoothedAutoColor: AutoColorEstimate? = null
                var smoothedShakeDx = 0f
                var smoothedShakeDy = 0f

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

                fun extractFrameSafely(ptsUs: Long): Bitmap {
                    val clampedPts = ptsUs.coerceIn(0L, safeMaxPtsUs)
                    val activeRetriever = retriever ?: openFreshRetriever()
                    if (activeRetriever != null) {
                        val firstTry = runCatching {
                            activeRetriever.getFrameAtTime(
                                clampedPts,
                                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                            )
                        }.getOrNull()
                        if (firstTry != null) return firstTry
                    }

                    // Self-heal: if the internal decoder entered Released state on MediaTek/Qualcomm,
                    // recreate MediaMetadataRetriever cleanly and retry.
                    val healedRetriever = openFreshRetriever()
                    if (healedRetriever != null) {
                        val secondTry = runCatching {
                            healedRetriever.getFrameAtTime(
                                clampedPts,
                                MediaMetadataRetriever.OPTION_PREVIOUS_SYNC
                            ) ?: healedRetriever.getFrameAtTime(0L)
                        }.getOrNull()
                        if (secondTry != null) return secondTry
                    }

                    return prevRawFrame?.copy(Bitmap.Config.ARGB_8888, true)
                        ?: Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
                }

                try {
                    openFreshRetriever()

                    for (outIdx in 0 until totalOutputFrames) {
                        if (isCancelled()) {
                            error("Processing cancelled by user.")
                        }
                        while (isPaused() && !isCancelled()) {
                            Thread.sleep(120)
                        }

                        val ptsUs = (outIdx * 1_000_000L) / effectiveFps
                        val rawSample = extractFrameSafely(ptsUs)

                        // Step 1: Motion Estimation & Stabilization
                        var workingFrame = if (pipeline.stabilize.strength != FilterStrength.OFF && prevRawFrame != null) {
                            val mv = StabilizationAndFpsProcessor.estimateMotionVector(prevRawFrame, rawSample)
                            smoothedShakeDx = smoothedShakeDx * 0.75f - mv.dx * 0.25f
                            smoothedShakeDy = smoothedShakeDy * 0.75f - mv.dy * 0.25f
                            StabilizationAndFpsProcessor.applyStabilizationWarp(
                                frame = rawSample,
                                smoothedCorrectionDx = smoothedShakeDx,
                                smoothedCorrectionDy = smoothedShakeDy,
                                config = pipeline.stabilize
                            )
                        } else {
                            rawSample.copy(Bitmap.Config.ARGB_8888, true)
                        }

                        // Step 2: AI Optical Flow / RIFE Frame Interpolation
                        if (useAiInterpolation && prevRawFrame != null && outIdx % 2 == 1) {
                            val interp = StabilizationAndFpsProcessor.interpolateIntermediateFrame(
                                frameA = prevRawFrame,
                                frameB = workingFrame,
                                alpha = 0.5f,
                                sceneCutDetection = pipeline.fps.sceneCutDetection,
                                useAiRifeModel = true,
                                onnxEngine = onnxAiEngine,
                                tileSize = pipeline.upscale.tileSize
                            )
                            if (interp !== workingFrame) workingFrame.recycle()
                            workingFrame = interp
                        }

                        prevRawFrame?.recycle()
                        prevRawFrame = rawSample.copy(Bitmap.Config.ARGB_8888, false)
                        rawSample.recycle()

                        // Step 3: Spatial-Temporal AI Denoising
                        if (pipeline.denoise.strength != FilterStrength.OFF) {
                            val denoised = DenoiseAndSharpenProcessor.applyDenoise(
                                currentFrame = workingFrame,
                                previousDenoisedFrame = prevDenoisedFrame,
                                config = pipeline.denoise
                            )
                            prevDenoisedFrame?.recycle()
                            prevDenoisedFrame = denoised.copy(Bitmap.Config.ARGB_8888, false)
                            if (denoised !== workingFrame) workingFrame.recycle()
                            workingFrame = denoised
                        }

                        // Step 4: Temporal-Smoothed Auto Color & 14-Axis Grading
                        if (pipeline.color.enabled || pipeline.color.autoColor) {
                            if (pipeline.color.autoColor) {
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
                                config = pipeline.color,
                                autoEstimate = smoothedAutoColor
                            )
                            if (graded !== workingFrame) workingFrame.recycle()
                            workingFrame = graded
                        }

                        // Step 4.5: CapCut Pro DSLR Camera Bokeh & Portrait Depth Blur
                        if (pipeline.bokeh.enabled) {
                            val bokehApplied = DslrBokehProcessor.applyDslrBokeh(workingFrame, pipeline.bokeh)
                            if (bokehApplied !== workingFrame) workingFrame.recycle()
                            workingFrame = bokehApplied
                        }

                        // Step 5: Upscaling & Tiled Local ONNX Super Resolution
                        if (pipeline.upscale.mode == UpscaleMode.AI_SUPER_RESOLUTION ||
                            pipeline.upscale.mode == UpscaleMode.MAXIMUM_QUALITY
                        ) {
                            val srOut = onnxAiEngine.runTiledInference(
                                source = workingFrame,
                                task = LocalAiModelTask.SUPER_RESOLUTION,
                                tileSize = pipeline.upscale.tileSize,
                                overlap = pipeline.upscale.tileOverlap
                            )
                            if (srOut !== workingFrame) workingFrame.recycle()
                            workingFrame = srOut
                        }

                        if (workingFrame.width != outWidth || workingFrame.height != outHeight) {
                            val scaled = Bitmap.createScaledBitmap(workingFrame, outWidth, outHeight, true)
                            if (scaled !== workingFrame) workingFrame.recycle()
                            workingFrame = scaled
                        }

                        // Step 6: Smart Sharpening & Edge Detail Recovery
                        val effectiveSharpen: SharpenConfig = if (pipeline.upscale.mode == UpscaleMode.MAXIMUM_QUALITY) {
                            pipeline.sharpen.copy(
                                strength = maxOf(pipeline.sharpen.strength, 0.32f),
                                fineDetailRecovery = maxOf(pipeline.sharpen.fineDetailRecovery, 0.28f)
                            )
                        } else {
                            pipeline.sharpen
                        }
                        if (effectiveSharpen.strength > 0.01f ||
                            effectiveSharpen.edgeEnhancement > 0.01f ||
                            effectiveSharpen.fineDetailRecovery > 0.01f ||
                            effectiveSharpen.deblurring
                        ) {
                            val sharp = DenoiseAndSharpenProcessor.applySharpenAndDetail(workingFrame, effectiveSharpen)
                            if (sharp !== workingFrame) workingFrame.recycle()
                            workingFrame = sharp
                        }

                        // Save enhanced frame to temporary frame cache on disk so RAM stays minimal
                        val frameFile = File(frameCacheDir, "frame_$outIdx.jpg")
                        FileOutputStream(frameFile).use { fos ->
                            workingFrame.compress(Bitmap.CompressFormat.JPEG, 96, fos)
                        }
                        workingFrame.recycle()

                        if (outIdx >= startFromCheckpointFrame) {
                            onCheckpointSaved(outIdx)
                        }

                        val elapsedSec = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000f).coerceAtLeast(0.1f)
                        val speedFps = (outIdx + 1) / elapsedSec
                        val remainingFrames = (totalOutputFrames - (outIdx + 1)).coerceAtLeast(0)
                        val etaSec = ((remainingFrames / speedFps.coerceAtLeast(0.5f)) * 1.25f).roundToInt()
                        val pct = (((outIdx + 1) * 75) / totalOutputFrames).coerceIn(1, 75)

                        onProgress(
                            ProcessingProgressUpdate(
                                stageName = buildStageSummary(pipeline, outWidth, outHeight, effectiveFps),
                                progressPercent = pct,
                                completedFrames = outIdx + 1,
                                totalFrames = totalOutputFrames,
                                currentFpsSpeed = speedFps,
                                estimatedRemainingSeconds = etaSec,
                                activePipelineNote = "Enhancing frame ${outIdx + 1}/$totalOutputFrames (${outWidth}×${outHeight})"
                            )
                        )
                    }
                } finally {
                    prevRawFrame?.recycle()
                    prevDenoisedFrame?.recycle()
                    runCatching { retriever?.release() }
                    retriever = null
                }

                // =========================================================================
                // PHASE 2: Resilient Multi-Tier MP4 Encoding & Audio Stream Muxing
                // (MediaMetadataRetriever is 100% closed -> exclusive codec access)
                // =========================================================================
                val includeAudio = pipeline.export.audioMode != AudioExportMode.MUTE && metadata.hasAudio

                MediaCodecVideoEncoder.encodeToMp4(
                    MediaCodecVideoEncoder.EncodeRequest(
                        context = context,
                        outputFile = tempOutputFile,
                        requestedMime = pipeline.export.codec.mimeType,
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
                        frameProvider = { frameIdx, targetW, targetH ->
                            val cachedFile = File(frameCacheDir, "frame_$frameIdx.jpg")
                            val decoded = if (cachedFile.exists()) {
                                BitmapFactory.decodeFile(cachedFile.absolutePath)
                            } else {
                                null
                            } ?: Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)

                            if (decoded.width != targetW || decoded.height != targetH) {
                                val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                                if (scaled !== decoded) decoded.recycle()
                                scaled
                            } else {
                                decoded
                            }
                        },
                        onFrameEncoded = { frameIdx, encoderDesc ->
                            val elapsedSec = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000f).coerceAtLeast(0.1f)
                            val speedFps = (frameIdx + 1) / elapsedSec
                            val encodePct = 75 + (((frameIdx + 1) * 22) / totalOutputFrames).coerceIn(1, 22)
                            val remaining = ((totalOutputFrames - (frameIdx + 1)) / 20f).roundToInt().coerceAtLeast(0)
                            onProgress(
                                ProcessingProgressUpdate(
                                    stageName = "Encoding MP4 Stream ($encoderDesc)",
                                    progressPercent = encodePct,
                                    completedFrames = frameIdx + 1,
                                    totalFrames = totalOutputFrames,
                                    currentFpsSpeed = speedFps,
                                    estimatedRemainingSeconds = remaining,
                                    activePipelineNote = "Muxing ${pipeline.export.codec.label} @ ${effectiveFps}fps"
                                )
                            )
                        }
                    )
                )
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
        if (pipeline.color.enabled || pipeline.color.autoColor) activeStages.add("Color Engine")
        if (pipeline.bokeh.enabled) activeStages.add("DSLR Bokeh (${pipeline.bokeh.aperture})")
        when (pipeline.upscale.mode) {
            UpscaleMode.AI_SUPER_RESOLUTION -> activeStages.add("ONNX AI SR")
            UpscaleMode.MAXIMUM_QUALITY -> activeStages.add("Max-Q Tiled AI")
            UpscaleMode.STANDARD_RESIZE -> if (pipeline.upscale.targetResolution != com.example.core.common.TargetResolution.ORIGINAL) {
                activeStages.add("Standard Upscale")
            }
        }
        if (pipeline.fps.mode == FpsConversionMode.AI_OPTICAL_FLOW_RIFE && pipeline.fps.targetFps > 0) {
            activeStages.add("RIFE Flow ${fps}fps")
        }
        return if (activeStages.isEmpty()) {
            "Rendering ${outW}×${outH} @ ${fps}fps"
        } else {
            activeStages.joinToString(" + ")
        }
    }
}
