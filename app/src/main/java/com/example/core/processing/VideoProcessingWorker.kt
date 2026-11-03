package com.example.core.processing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.core.ai.OnnxAiEngine
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.TargetResolution
import com.example.core.common.UpscaleConfig
import com.example.core.common.UpscaleMode
import com.example.core.gpu.DeviceCapabilityAnalyzer
import com.example.core.media.VideoAnalyzer
import com.example.core.storage.MediaStoreVideoSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_TARGET_RES = "target_res"
        const val KEY_UPSCALE_MODE = "upscale_mode"
        const val KEY_PROGRESS_PCT = "progress_pct"
        const val KEY_STAGE = "stage"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_OUTPUT_PATH = "output_path"
        private const val CHANNEL_ID = "videox_processing_channel"
        private const val NOTIFICATION_ID = 4102
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val uriStr = inputData.getString(KEY_SOURCE_URI) ?: return@withContext Result.failure()
        val resName = inputData.getString(KEY_TARGET_RES) ?: TargetResolution.HD_720P.name
        val modeName = inputData.getString(KEY_UPSCALE_MODE) ?: UpscaleMode.STANDARD_RESIZE.name

        val targetRes = TargetResolution.entries.firstOrNull { it.name == resName } ?: TargetResolution.HD_720P
        val upscaleMode = UpscaleMode.entries.firstOrNull { it.name == modeName } ?: UpscaleMode.STANDARD_RESIZE

        runCatching {
            setForeground(createForegroundInfo("Initializing local video pipeline...", 0))
        }

        val analyzer = VideoAnalyzer(applicationContext)
        val metadata = analyzer.analyzeVideo(android.net.Uri.parse(uriStr))
        if (!metadata.isSupported) {
            return@withContext Result.failure(workDataOf("error" to (metadata.unsupportedReason ?: "Unsupported video")))
        }

        val engine = VideoProcessingEngine(
            context = applicationContext,
            onnxAiEngine = OnnxAiEngine(applicationContext),
            capabilityAnalyzer = DeviceCapabilityAnalyzer(applicationContext),
            mediaStoreSaver = MediaStoreVideoSaver(applicationContext)
        )

        val pipeline = EnhancementPipelineState(
            upscale = UpscaleConfig(targetResolution = targetRes, mode = upscaleMode)
        )

        val exportResult = engine.processAndExportVideo(
            metadata = metadata,
            pipeline = pipeline,
            isCancelled = { isStopped },
            isPaused = { false },
            onProgress = { prog ->
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS_PCT to prog.progressPercent,
                        KEY_STAGE to prog.stageName
                    )
                )
            }
        )

        exportResult.fold(
            onSuccess = { saved ->
                Result.success(
                    workDataOf(
                        KEY_OUTPUT_URI to saved.galleryUri.toString(),
                        KEY_OUTPUT_PATH to saved.displayPath
                    )
                )
            },
            onFailure = { err ->
                Result.failure(workDataOf("error" to (err.localizedMessage ?: "Export failed")))
            }
        )
    }

    private fun createForegroundInfo(stage: String, progress: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VideoX AI Local Processing",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("VideoX AI • On-Device Processing")
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
