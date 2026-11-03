package com.example.core.processing

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.TargetResolution
import com.example.core.common.UpscaleMode
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QueueTaskStatus(val label: String) {
    QUEUED("Queued"),
    PREPROCESSING("Preprocessing & Sampling"),
    CLOUD_AI_ANALYZING("Gemini AI Director Analysis"),
    ENHANCING("AI Upscale & Bokeh"),
    ENCODING("Hardware MP4 Encoding"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

data class AiVideoQueueTask(
    val id: String = UUID.randomUUID().toString(),
    val sourceUriString: String,
    val fileName: String,
    val pipeline: EnhancementPipelineState = EnhancementPipelineState(),
    val status: QueueTaskStatus = QueueTaskStatus.QUEUED,
    val progressPercent: Int = 0,
    val stageMessage: String = "Waiting in queue...",
    val outputUriString: String? = null,
    val outputDisplayPath: String? = null,
    val addedTimestampMs: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

class AiVideoEnhancementQueueManager private constructor(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _tasks = MutableStateFlow<List<AiVideoQueueTask>>(emptyList())
    val tasks: StateFlow<List<AiVideoQueueTask>> = _tasks.asStateFlow()

    fun enqueueEnhancementTask(
        sourceUri: Uri,
        fileName: String,
        pipeline: EnhancementPipelineState
    ): String {
        val taskId = UUID.randomUUID().toString()
        val newTask = AiVideoQueueTask(
            id = taskId,
            sourceUriString = sourceUri.toString(),
            fileName = fileName,
            pipeline = pipeline,
            status = QueueTaskStatus.QUEUED,
            stageMessage = "Task queued for processing"
        )

        _tasks.update { listOf(newTask) + it }

        val workInput = Data.Builder()
            .putString(AiVideoEnhancementWorker.KEY_TASK_ID, taskId)
            .putString(AiVideoEnhancementWorker.KEY_SOURCE_URI, sourceUri.toString())
            .putString(AiVideoEnhancementWorker.KEY_FILE_NAME, fileName)
            .putString(AiVideoEnhancementWorker.KEY_TARGET_RES, pipeline.upscale.targetResolution.name)
            .putString(AiVideoEnhancementWorker.KEY_UPSCALE_MODE, pipeline.upscale.mode.name)
            .putBoolean(AiVideoEnhancementWorker.KEY_CLOUD_AI, pipeline.aiDirector.cloudAiEnabled)
            .putBoolean(AiVideoEnhancementWorker.KEY_BOKEH_ENABLED, pipeline.bokeh.enabled)
            .putString(AiVideoEnhancementWorker.KEY_BOKEH_APERTURE, pipeline.bokeh.aperture)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<AiVideoEnhancementWorker>()
            .setInputData(workInput)
            .addTag("video_ai_task_$taskId")
            .build()

        workManager.enqueueUniqueWork(
            "videox_task_$taskId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        // Observe WorkManager progress and state updates
        observeWorkTask(taskId, workRequest.id)

        return taskId
    }

    private fun observeWorkTask(taskId: String, workId: UUID) {
        scope.launch(Dispatchers.Main) {
            workManager.getWorkInfoByIdLiveData(workId).observeForever { workInfo ->
                if (workInfo == null) return@observeForever

                val progress = workInfo.progress
                val pct = progress.getInt(AiVideoEnhancementWorker.KEY_PROGRESS_PCT, 0)
                val stage = progress.getString(AiVideoEnhancementWorker.KEY_STAGE)

                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        val status = when {
                            pct < 15 -> QueueTaskStatus.PREPROCESSING
                            pct in 15..35 -> QueueTaskStatus.CLOUD_AI_ANALYZING
                            pct in 36..80 -> QueueTaskStatus.ENHANCING
                            else -> QueueTaskStatus.ENCODING
                        }
                        updateTask(taskId) { task ->
                            task.copy(
                                status = status,
                                progressPercent = pct.coerceAtLeast(task.progressPercent),
                                stageMessage = stage ?: task.stageMessage
                            )
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val outUri = workInfo.outputData.getString(AiVideoEnhancementWorker.KEY_OUTPUT_URI)
                        val outPath = workInfo.outputData.getString(AiVideoEnhancementWorker.KEY_OUTPUT_PATH)
                        updateTask(taskId) { task ->
                            task.copy(
                                status = QueueTaskStatus.COMPLETED,
                                progressPercent = 100,
                                stageMessage = "Finished & Saved to Movies/VideoX AI",
                                outputUriString = outUri,
                                outputDisplayPath = outPath
                            )
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val err = workInfo.outputData.getString("error") ?: "Enhancement failed."
                        updateTask(taskId) { task ->
                            task.copy(
                                status = QueueTaskStatus.FAILED,
                                stageMessage = "Error: $err",
                                errorMessage = err
                            )
                        }
                    }
                    WorkInfo.State.CANCELLED -> {
                        updateTask(taskId) { task ->
                            task.copy(
                                status = QueueTaskStatus.CANCELLED,
                                stageMessage = "Task was cancelled."
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun updateTask(taskId: String, transform: (AiVideoQueueTask) -> AiVideoQueueTask) {
        _tasks.update { list ->
            list.map { if (it.id == taskId) transform(it) else it }
        }
    }

    fun cancelTask(taskId: String) {
        workManager.cancelUniqueWork("videox_task_$taskId")
        updateTask(taskId) {
            it.copy(status = QueueTaskStatus.CANCELLED, stageMessage = "Cancelled by user.")
        }
    }

    fun removeTask(taskId: String) {
        cancelTask(taskId)
        _tasks.update { list -> list.filterNot { it.id == taskId } }
    }

    fun clearCompleted() {
        _tasks.update { list ->
            list.filter { it.status == QueueTaskStatus.QUEUED || it.status == QueueTaskStatus.ENHANCING || it.status == QueueTaskStatus.PREPROCESSING }
        }
    }

    companion object {
        @Volatile
        private var instance: AiVideoEnhancementQueueManager? = null

        fun getInstance(context: Context): AiVideoEnhancementQueueManager {
            return instance ?: synchronized(this) {
                instance ?: AiVideoEnhancementQueueManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
