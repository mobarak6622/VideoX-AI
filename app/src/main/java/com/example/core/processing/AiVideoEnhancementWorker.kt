package com.example.core.processing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.core.ai.GeminiVideoDirectorEngine
import com.example.core.ai.OnnxAiEngine
import com.example.core.common.AiDirectorConfig
import com.example.core.common.DslrBokehConfig
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.TargetResolution
import com.example.core.common.UpscaleConfig
import com.example.core.common.UpscaleMode
import com.example.core.gpu.DeviceCapabilityAnalyzer
import com.example.core.media.VideoAnalyzer
import com.example.core.storage.MediaStoreVideoSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiVideoEnhancementWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TASK_ID = "task_id"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_TARGET_RES = "target_res"
        const val KEY_UPSCALE_MODE = "upscale_mode"
        const val KEY_CLOUD_AI = "cloud_ai_enabled"
        const val KEY_BOKEH_ENABLED = "bokeh_enabled"
        const val KEY_BOKEH_APERTURE = "bokeh_aperture"
        const val KEY_PROGRESS_PCT = "progress_pct"
        const val KEY_STAGE = "stage"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_OUTPUT_PATH = "output_path"
        private const val CHANNEL_ID = "videox_queue_channel"
        private const val NOTIFICATION_ID = 5104
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val uriStr = inputData.getString(KEY_SOURCE_URI) ?: return@withContext Result.failure()
        val targetResName = inputData.getString(KEY_TARGET_RES) ?: TargetResolution.HD_720P.name
        val upscaleModeName = inputData.getString(KEY_UPSCALE_MODE) ?: UpscaleMode.AI_SUPER_RESOLUTION.name
        val cloudAiEnabled = inputData.getBoolean(KEY_CLOUD_AI, true)
        val bokehEnabled = inputData.getBoolean(KEY_BOKEH_ENABLED, true)
        val bokehAperture = inputData.getString(KEY_BOKEH_APERTURE) ?: "f/1.8"

        val targetRes = TargetResolution.entries.firstOrNull { it.name == targetResName } ?: TargetResolution.HD_720P
        val upscaleMode = UpscaleMode.entries.firstOrNull { it.name == upscaleModeName } ?: UpscaleMode.AI_SUPER_RESOLUTION

        runCatching {
            setForeground(createForegroundInfo("Loading video file & initializing AI pipeline...", 5))
        }

        // 1. Loading & Preprocessing
        setProgress(workDataOf(KEY_PROGRESS_PCT to 8, KEY_STAGE to "Loading video container & analyzing metadata"))
        val analyzer = VideoAnalyzer(applicationContext)
        val metadata = analyzer.analyzeVideo(Uri.parse(uriStr))
        if (!metadata.isSupported) {
            return@withContext Result.failure(workDataOf("error" to (metadata.unsupportedReason ?: "Unsupported video format.")))
        }

        var effectivePipeline = EnhancementPipelineState(
            upscale = UpscaleConfig(targetResolution = targetRes, mode = upscaleMode),
            bokeh = DslrBokehConfig(enabled = bokehEnabled, aperture = bokehAperture),
            aiDirector = AiDirectorConfig(cloudAiEnabled = cloudAiEnabled)
        )

        // 2. Cloud AI Gemini Analysis (if enabled)
        if (cloudAiEnabled) {
            setProgress(workDataOf(KEY_PROGRESS_PCT to 20, KEY_STAGE to "Gemini AI Director analyzing lighting & DSLR bokeh"))
            runCatching {
                setForeground(createForegroundInfo("Gemini AI Director analyzing video frames...", 20))
            }

            val sampleFrame = analyzer.extractFrameAt(Uri.parse(uriStr), (metadata.durationMs * 500L).coerceAtLeast(0L), 640)
            if (sampleFrame != null) {
                val directorEngine = GeminiVideoDirectorEngine(applicationContext)
                val plan = directorEngine.analyzeFrameAndGenerateProPlan(sampleFrame, "${metadata.width}×${metadata.height}")
                sampleFrame.recycle()

                effectivePipeline = effectivePipeline.copy(
                    color = plan.recommendedColor,
                    bokeh = if (bokehEnabled) plan.recommendedBokeh else effectivePipeline.bokeh,
                    sharpen = plan.recommendedSharpen,
                    aiDirector = effectivePipeline.aiDirector.copy(
                        directorNotes = plan.directorAdviceBengali
                    )
                )
            }
        }

        // 3. Execution of Enhanced Video Processing Pipeline
        setProgress(workDataOf(KEY_PROGRESS_PCT to 35, KEY_STAGE to "Rendering CapCut Pro AI Video Enhancement"))
        runCatching {
            setForeground(createForegroundInfo("Enhancing frames (${targetRes.shortBadge} @ ${effectivePipeline.bokeh.aperture})...", 35))
        }

        val engine = VideoProcessingEngine(
            context = applicationContext,
            onnxAiEngine = OnnxAiEngine(applicationContext),
            capabilityAnalyzer = DeviceCapabilityAnalyzer(applicationContext),
            mediaStoreSaver = MediaStoreVideoSaver(applicationContext)
        )

        val result = engine.processAndExportVideo(
            metadata = metadata,
            pipeline = effectivePipeline,
            isCancelled = { isStopped },
            isPaused = { false },
            onProgress = { prog ->
                val scaledProgress = 35 + ((prog.progressPercent * 63) / 100)
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS_PCT to scaledProgress,
                        KEY_STAGE to "${prog.stageName} (${prog.progressPercent}%)"
                    )
                )
                runCatching {
                    setForegroundAsync(createForegroundInfo(prog.stageName, scaledProgress))
                }
            }
        )

        result.fold(
            onSuccess = { saved ->
                Result.success(
                    workDataOf(
                        KEY_OUTPUT_URI to saved.galleryUri.toString(),
                        KEY_OUTPUT_PATH to saved.displayPath
                    )
                )
            },
            onFailure = { err ->
                Result.failure(workDataOf("error" to (err.localizedMessage ?: "Processing error occurred.")))
            }
        )
    }

    private fun createForegroundInfo(stage: String, progress: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VideoX AI Pro Processing Queue",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("🎬 VideoX AI Pro • Video Processing Queue")
            .setContentText("$stage ($progress%)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
