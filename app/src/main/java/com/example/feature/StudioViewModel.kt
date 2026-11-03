package com.example.feature

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ai.GeminiVideoDirectorEngine
import com.example.core.ai.InstalledAiModelInfo
import com.example.core.ai.LocalAiModelTask
import com.example.core.ai.OnnxAiEngine
import com.example.core.common.AppLanguage
import com.example.core.common.ColorConfig
import com.example.core.common.DenoiseConfig
import com.example.core.common.DslrBokehConfig
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.ExportConfig
import com.example.core.common.FpsConfig
import com.example.core.common.PreviewQuality
import com.example.core.common.ProcessingProfile
import com.example.core.common.SharpenConfig
import com.example.core.common.StabilizeConfig
import com.example.core.common.UpscaleConfig
import com.example.core.common.VideoMetadata
import com.example.core.gpu.DeviceCapabilitiesReport
import com.example.core.gpu.DeviceCapabilityAnalyzer
import com.example.core.media.SampleVideoGenerator
import com.example.core.media.VideoAnalyzer
import com.example.core.processing.AiVideoEnhancementQueueManager
import com.example.core.processing.AiVideoQueueTask
import com.example.core.processing.ColorCorrectionProcessor
import com.example.core.processing.ProcessingProgressUpdate
import com.example.core.processing.VideoProcessingEngine
import com.example.core.storage.MediaStoreVideoSaver
import com.example.core.storage.SavedGalleryVideoResult
import com.example.data.database.VideoProjectEntity
import com.example.data.database.VideoXDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettingsState
import com.example.data.repository.VideoProjectRepository
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StudioScreen {
    ONBOARDING,
    HOME,
    VIDEO_ANALYSIS,
    VIDEO_PREVIEW,
    EDITOR,
    AI_UPSCALER,
    AUTO_COLOR,
    DSLR_BOKEH,
    QUEUE_MANAGER,
    FPS_CONVERTER,
    STABILIZER,
    DENOISER,
    SHARPEN_DETAIL,
    EXPORT_SETTINGS,
    PROCESSING,
    EXPORT_SUCCESS,
    PROJECTS,
    TOOLS_HUB,
    AI_MODEL_MANAGER,
    DEVICE_COMPATIBILITY,
    PRIVACY_INFO,
    SETTINGS
}

data class ActiveProcessingState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val stageName: String = "Idle",
    val progressPercent: Int = 0,
    val completedFrames: Int = 0,
    val totalFrames: Int = 0,
    val currentFpsSpeed: Float = 0f,
    val estimatedRemainingSeconds: Int = 0,
    val activePipelineNote: String = "",
    val lastCheckpointFrame: Int = 0,
    val errorMessage: String? = null,
    val completedResult: SavedGalleryVideoResult? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = VideoXDatabase.getInstance(context)
    private val projectRepository = VideoProjectRepository(database.projectDao())
    private val prefsRepository = UserPreferencesRepository(context)
    private val capabilityAnalyzer = DeviceCapabilityAnalyzer(context)
    private val onnxAiEngine = OnnxAiEngine(context)
    private val videoAnalyzer = VideoAnalyzer(context)
    private val sampleVideoGenerator = SampleVideoGenerator(context)
    private val mediaStoreSaver = MediaStoreVideoSaver(context)
    private val apkExportHelper = com.example.core.storage.ApkExportHelper(context)
    private val queueManager = AiVideoEnhancementQueueManager.getInstance(context)
    private val geminiDirector = GeminiVideoDirectorEngine(context)
    private val processingEngine = VideoProcessingEngine(
        context = context,
        onnxAiEngine = onnxAiEngine,
        capabilityAnalyzer = capabilityAnalyzer,
        mediaStoreSaver = mediaStoreSaver
    )

    val userSettings: StateFlow<UserSettingsState> = prefsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserSettingsState()
    )

    val projects: StateFlow<List<VideoProjectEntity>> = projectRepository.allProjects.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val queueTasks: StateFlow<List<AiVideoQueueTask>> = queueManager.tasks

    private val _lastProcessedVideoUri = MutableStateFlow<String?>(null)
    val lastProcessedVideoUri: StateFlow<String?> = _lastProcessedVideoUri.asStateFlow()

    private val _isAnalyzingWithGemini = MutableStateFlow(false)
    val isAnalyzingWithGemini: StateFlow<Boolean> = _isAnalyzingWithGemini.asStateFlow()

    private val _currentScreen = MutableStateFlow(StudioScreen.HOME)
    val currentScreen: StateFlow<StudioScreen> = _currentScreen.asStateFlow()

    private val _screenBackStack = ArrayDeque<StudioScreen>()

    private val _currentVideo = MutableStateFlow<VideoMetadata?>(null)
    val currentVideo: StateFlow<VideoMetadata?> = _currentVideo.asStateFlow()

    private val _pipeline = MutableStateFlow(EnhancementPipelineState())
    val pipeline: StateFlow<EnhancementPipelineState> = _pipeline.asStateFlow()

    private val undoStack = ArrayDeque<EnhancementPipelineState>()
    private val redoStack = ArrayDeque<EnhancementPipelineState>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _originalPreviewFrame = MutableStateFlow<Bitmap?>(null)
    val originalPreviewFrame: StateFlow<Bitmap?> = _originalPreviewFrame.asStateFlow()

    private val _enhancedPreviewFrame = MutableStateFlow<Bitmap?>(null)
    val enhancedPreviewFrame: StateFlow<Bitmap?> = _enhancedPreviewFrame.asStateFlow()

    private val _previewTimestampMs = MutableStateFlow(0L)
    val previewTimestampMs: StateFlow<Long> = _previewTimestampMs.asStateFlow()

    private val _deviceCapabilities = MutableStateFlow<DeviceCapabilitiesReport?>(null)
    val deviceCapabilities: StateFlow<DeviceCapabilitiesReport?> = _deviceCapabilities.asStateFlow()

    private val _installedModels = MutableStateFlow<List<InstalledAiModelInfo>>(emptyList())
    val installedModels: StateFlow<List<InstalledAiModelInfo>> = _installedModels.asStateFlow()

    private val _processingState = MutableStateFlow(ActiveProcessingState())
    val processingState: StateFlow<ActiveProcessingState> = _processingState.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isGeneratingSample = MutableStateFlow(false)
    val isGeneratingSample: StateFlow<Boolean> = _isGeneratingSample.asStateFlow()

    private var activeExportJob: Job? = null
    private val cancelFlag = AtomicBoolean(false)
    private val pauseFlag = AtomicBoolean(false)

    init {
        refreshHardwareAndModels()
    }

    fun refreshHardwareAndModels() {
        viewModelScope.launch(Dispatchers.IO) {
            val caps = capabilityAnalyzer.analyze()
            _deviceCapabilities.value = caps
            val models = onnxAiEngine.ensureBundledModelsExtracted()
            _installedModels.value = models
        }
    }

    fun navigateTo(screen: StudioScreen) {
        val current = _currentScreen.value
        if (current != screen) {
            _screenBackStack.addLast(current)
            _currentScreen.value = screen
        }
    }

    fun navigateBack(): Boolean {
        return if (_screenBackStack.isNotEmpty()) {
            _currentScreen.value = _screenBackStack.removeLast()
            true
        } else if (_currentScreen.value != StudioScreen.HOME) {
            _currentScreen.value = StudioScreen.HOME
            true
        } else {
            false
        }
    }

    fun dismissBanner() {
        _statusBannerMessage.value = null
    }

    fun importVideoUri(uri: Uri, navigateToAnalysis: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val meta = videoAnalyzer.analyzeVideo(uri)
            _currentVideo.value = meta
            if (meta.isSupported) {
                _previewTimestampMs.value = (meta.durationMs / 4L).coerceAtLeast(0L)
                extractAndRefreshPreviewFrames(uri, _previewTimestampMs.value)
                // Save as draft project in Room DB
                projectRepository.saveOrUpdateProject(
                    VideoProjectEntity(
                        projectName = meta.fileName.substringBeforeLast('.').ifBlank { "VideoX Project" },
                        sourceUriString = meta.uriString,
                        exportedUriString = null,
                        exportedDisplayPath = null,
                        sourceResolution = "${meta.width}×${meta.height}",
                        targetResolution = _pipeline.value.upscale.targetResolution.shortBadge,
                        sourceFps = meta.frameRate,
                        targetFps = meta.frameRate.toInt(),
                        codecLabel = meta.videoCodec,
                        upscaleModeLabel = _pipeline.value.upscale.mode.title,
                        colorPresetLabel = _pipeline.value.color.preset.displayName,
                        durationMs = meta.durationMs,
                        outputFileSizeBytes = meta.fileSizeBytes,
                        status = "IMPORTED"
                    )
                )
                if (navigateToAnalysis) {
                    withContext(Dispatchers.Main) {
                        navigateTo(StudioScreen.VIDEO_ANALYSIS)
                    }
                }
            } else {
                _statusBannerMessage.value = meta.unsupportedReason ?: "Unsupported video format."
                if (navigateToAnalysis) {
                    withContext(Dispatchers.Main) {
                        navigateTo(StudioScreen.VIDEO_ANALYSIS)
                    }
                }
            }
        }
    }

    fun generateAndImportSampleVideo(
        width: Int = 848,
        height: Int = 480,
        fps: Int = 24,
        label: String = "VideoX_Studio_Sample_480p.mp4"
    ) {
        if (_isGeneratingSample.value) return
        _isGeneratingSample.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val res = sampleVideoGenerator.generateSampleMp4(
                width = width,
                height = height,
                fps = fps,
                durationSeconds = 2,
                fileName = label
            )
            _isGeneratingSample.value = false
            res.fold(
                onSuccess = { uri ->
                    _statusBannerMessage.value = "Generated real playable MP4 test stream ($label)"
                    importVideoUri(uri, navigateToAnalysis = true)
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Could not synthesize sample video: ${err.localizedMessage}"
                }
            )
        }
    }

    fun seekPreviewTimestamp(timestampMs: Long) {
        val meta = _currentVideo.value ?: return
        val clamped = timestampMs.coerceIn(0L, meta.durationMs.coerceAtLeast(100L))
        _previewTimestampMs.value = clamped
        viewModelScope.launch(Dispatchers.IO) {
            extractAndRefreshPreviewFrames(Uri.parse(meta.uriString), clamped)
        }
    }

    private suspend fun extractAndRefreshPreviewFrames(uri: Uri, timestampMs: Long) {
        val maxDim = _pipeline.value.previewQuality.maxPreviewDimension
        val raw = videoAnalyzer.extractFrameAt(uri, timestampMs * 1000L, maxDim) ?: return
        _originalPreviewFrame.value = raw
        val enhanced = processingEngine.enhanceSinglePreviewFrame(raw, _pipeline.value)
        _enhancedPreviewFrame.value = enhanced
    }

    private fun refreshEnhancedFrameOnly() {
        val raw = _originalPreviewFrame.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val enhanced = processingEngine.enhanceSinglePreviewFrame(raw, _pipeline.value)
            _enhancedPreviewFrame.value = enhanced
        }
    }

    private fun recordUndoAndUpdate(mutator: (EnhancementPipelineState) -> EnhancementPipelineState) {
        undoStack.addLast(_pipeline.value)
        if (undoStack.size > 25) undoStack.removeFirst()
        redoStack.clear()
        _pipeline.update(mutator)
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        refreshEnhancedFrameOnly()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.addLast(_pipeline.value)
        val previous = undoStack.removeLast()
        _pipeline.value = previous
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        refreshEnhancedFrameOnly()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.addLast(_pipeline.value)
        val next = redoStack.removeLast()
        _pipeline.value = next
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        refreshEnhancedFrameOnly()
    }

    fun resetAllEnhancements() {
        val currentPreview = _pipeline.value.previewQuality
        recordUndoAndUpdate { EnhancementPipelineState(previewQuality = currentPreview) }
        _statusBannerMessage.value = "All enhancement parameters reset to original stream."
    }

    fun updateUpscaleConfig(config: UpscaleConfig) {
        recordUndoAndUpdate { it.copy(upscale = config, export = it.export.copy(resolution = config.targetResolution)) }
    }

    fun updateColorConfig(config: ColorConfig) {
        recordUndoAndUpdate { it.copy(color = config) }
    }

    fun applyOneTapAutoColor() {
        val raw = _originalPreviewFrame.value
        val est = if (raw != null) ColorCorrectionProcessor.analyzeFrameForAutoColor(raw) else null
        recordUndoAndUpdate { state ->
            state.copy(
                color = state.color.copy(
                    enabled = true,
                    autoColor = true,
                    exposure = est?.exposure ?: 0.1f,
                    contrast = est?.contrast ?: 0.12f,
                    saturation = est?.saturation ?: 0.1f,
                    temperature = est?.temperature ?: 0.0f,
                    tint = est?.tint ?: 0.0f,
                    shadows = est?.shadows ?: 0.08f,
                    highlights = est?.highlights ?: -0.06f
                )
            )
        }
        _statusBannerMessage.value = "Auto Color analyzed frame luminance & applied skin-safe balanced grading."
    }

    fun updateFpsConfig(config: FpsConfig) {
        recordUndoAndUpdate { it.copy(fps = config, export = it.export.copy(targetFps = config.targetFps)) }
    }

    fun updateDenoiseConfig(config: DenoiseConfig) {
        recordUndoAndUpdate { it.copy(denoise = config) }
    }

    fun updateStabilizeConfig(config: StabilizeConfig) {
        recordUndoAndUpdate { it.copy(stabilize = config) }
    }

    fun updateSharpenConfig(config: SharpenConfig) {
        recordUndoAndUpdate { it.copy(sharpen = config) }
    }

    fun updateBokehConfig(config: DslrBokehConfig) {
        recordUndoAndUpdate { it.copy(bokeh = config) }
    }

    fun applyGeminiCapCutProDirector() {
        val meta = _currentVideo.value ?: return
        val raw = _originalPreviewFrame.value ?: return
        if (_isAnalyzingWithGemini.value) return
        _isAnalyzingWithGemini.value = true
        _statusBannerMessage.value = "Gemini AI Director analyzing lighting, skin tones & scene depth..."

        viewModelScope.launch(Dispatchers.Default) {
            val plan = geminiDirector.analyzeFrameAndGenerateProPlan(raw, "${meta.width}×${meta.height}")
            _isAnalyzingWithGemini.value = false

            recordUndoAndUpdate { state ->
                state.copy(
                    color = plan.recommendedColor,
                    bokeh = plan.recommendedBokeh,
                    sharpen = plan.recommendedSharpen,
                    aiDirector = state.aiDirector.copy(
                        activeGenrePreset = plan.genreTitle,
                        directorNotes = plan.directorAdviceBengali
                    )
                )
            }

            _statusBannerMessage.value = "CapCut Pro AI Applied: ${plan.genreTitle} • ${plan.directorAdviceBengali}"
        }
    }

    fun queueCurrentVideoForEnhancement() {
        val meta = _currentVideo.value
        if (meta == null || !meta.isSupported) {
            _statusBannerMessage.value = "Please select a valid video before queueing."
            return
        }
        val taskId = queueManager.enqueueEnhancementTask(
            sourceUri = Uri.parse(meta.uriString),
            fileName = meta.fileName,
            pipeline = _pipeline.value
        )
        _statusBannerMessage.value = "Added '${meta.fileName}' to Background AI Processing Queue."
        navigateTo(StudioScreen.QUEUE_MANAGER)
    }

    fun cancelQueueTask(taskId: String) {
        queueManager.cancelTask(taskId)
    }

    fun removeQueueTask(taskId: String) {
        queueManager.removeTask(taskId)
    }

    fun clearCompletedQueueTasks() {
        queueManager.clearCompleted()
        _statusBannerMessage.value = "Cleared completed queue jobs."
    }

    fun updateExportConfig(config: ExportConfig) {
        recordUndoAndUpdate { it.copy(export = config) }
    }

    fun updatePreviewQuality(quality: PreviewQuality) {
        _pipeline.update { it.copy(previewQuality = quality) }
        viewModelScope.launch {
            prefsRepository.setPreviewQuality(quality)
            val meta = _currentVideo.value
            if (meta != null) {
                extractAndRefreshPreviewFrames(Uri.parse(meta.uriString), _previewTimestampMs.value)
            }
        }
    }

    fun estimateExportSizeAndBitrate(): Pair<Int, Long> {
        val meta = _currentVideo.value ?: return Pair(8_000_000, 0L)
        return processingEngine.estimateExportBitrateAndSize(meta, _pipeline.value)
    }

    fun startRealExportProcessing(resumeFromCheckpoint: Boolean = false) {
        val meta = _currentVideo.value
        if (meta == null || !meta.isSupported) {
            _statusBannerMessage.value = "Please import or generate a valid video first."
            return
        }

        if (_processingState.value.isRunning) return

        cancelFlag.set(false)
        pauseFlag.set(false)
        val checkpoint = if (resumeFromCheckpoint) _processingState.value.lastCheckpointFrame else 0

        _processingState.value = ActiveProcessingState(
            isRunning = true,
            isPaused = false,
            stageName = "Initializing Hardware MediaCodec & ONNX Pipeline...",
            progressPercent = 1,
            completedFrames = checkpoint,
            totalFrames = meta.estimatedTotalFrames,
            lastCheckpointFrame = checkpoint
        )
        navigateTo(StudioScreen.PROCESSING)

        activeExportJob = viewModelScope.launch(Dispatchers.Default) {
            val result = processingEngine.processAndExportVideo(
                metadata = meta,
                pipeline = _pipeline.value,
                isCancelled = { cancelFlag.get() },
                isPaused = { pauseFlag.get() },
                startFromCheckpointFrame = checkpoint,
                onCheckpointSaved = { frameIdx ->
                    _processingState.update { it.copy(lastCheckpointFrame = frameIdx) }
                },
                onProgress = { prog: ProcessingProgressUpdate ->
                    _processingState.update {
                        it.copy(
                            isRunning = true,
                            isPaused = pauseFlag.get(),
                            stageName = prog.stageName,
                            progressPercent = prog.progressPercent,
                            completedFrames = prog.completedFrames,
                            totalFrames = prog.totalFrames,
                            currentFpsSpeed = prog.currentFpsSpeed,
                            estimatedRemainingSeconds = prog.estimatedRemainingSeconds,
                            activePipelineNote = prog.activePipelineNote
                        )
                    }
                }
            )

            result.fold(
                onSuccess = { saved ->
                    val effectiveRes = if (_pipeline.value.export.resolution != com.example.core.common.TargetResolution.ORIGINAL) {
                        _pipeline.value.export.resolution
                    } else {
                        _pipeline.value.upscale.targetResolution
                    }
                    val effectiveFps = when {
                        _pipeline.value.export.targetFps > 0 -> _pipeline.value.export.targetFps
                        _pipeline.value.fps.targetFps > 0 -> _pipeline.value.fps.targetFps
                        else -> meta.frameRate.toInt()
                    }
                    projectRepository.saveOrUpdateProject(
                        VideoProjectEntity(
                            projectName = "${meta.fileName.substringBeforeLast('.')} (${effectiveRes.shortBadge})",
                            sourceUriString = meta.uriString,
                            exportedUriString = saved.galleryUri.toString(),
                            exportedDisplayPath = saved.displayPath,
                            sourceResolution = "${meta.width}×${meta.height}",
                            targetResolution = "${saved.verifiedWidth}×${saved.verifiedHeight}",
                            sourceFps = meta.frameRate,
                            targetFps = effectiveFps,
                            codecLabel = _pipeline.value.export.codec.label,
                            upscaleModeLabel = _pipeline.value.upscale.mode.title,
                            colorPresetLabel = _pipeline.value.color.preset.displayName,
                            durationMs = saved.verifiedDurationMs.takeIf { it > 0 } ?: meta.durationMs,
                            outputFileSizeBytes = saved.fileSizeBytes,
                            status = "COMPLETED",
                            lastCheckpointFrame = _processingState.value.totalFrames
                        )
                    )
                    _lastProcessedVideoUri.value = saved.galleryUri.toString()
                    _processingState.update {
                        it.copy(
                            isRunning = false,
                            isPaused = false,
                            stageName = "Export Verified & Saved",
                            progressPercent = 100,
                            completedResult = saved,
                            errorMessage = null
                        )
                    }
                    withContext(Dispatchers.Main) {
                        navigateTo(StudioScreen.EXPORT_SUCCESS)
                    }
                },
                onFailure = { err ->
                    _processingState.update {
                        it.copy(
                            isRunning = false,
                            isPaused = false,
                            stageName = if (cancelFlag.get()) "Cancelled" else "Processing Halted",
                            errorMessage = err.localizedMessage ?: "Video encoding failed."
                        )
                    }
                }
            )
        }
    }

    fun togglePauseProcessing() {
        val nextPaused = !pauseFlag.get()
        pauseFlag.set(nextPaused)
        _processingState.update {
            it.copy(
                isPaused = nextPaused,
                stageName = if (nextPaused) "Paused at Checkpoint Frame #${it.lastCheckpointFrame}" else "Resuming Pipeline..."
            )
        }
    }

    fun cancelProcessing() {
        cancelFlag.set(true)
        pauseFlag.set(false)
        activeExportJob?.cancel()
        _processingState.update {
            it.copy(
                isRunning = false,
                isPaused = false,
                stageName = "Cancelled (Checkpoint saved at frame #${it.lastCheckpointFrame})",
                errorMessage = "Processing cancelled by user. Original video is untouched."
            )
        }
    }

    // AI Model Manager Actions
    fun benchmarkAiModel(task: LocalAiModelTask) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = onnxAiEngine.benchmarkModel(task)
            _installedModels.value = onnxAiEngine.getInstalledModels()
            res.fold(
                onSuccess = { ms ->
                    _statusBannerMessage.value = "ONNX Runtime Mobile verified ${task.title}: ${ms}ms per 64×64 tile"
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Model benchmark failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun importCustomOnnxModel(task: LocalAiModelTask, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = onnxAiEngine.importCustomOnnxModel(task, uri)
            _installedModels.value = onnxAiEngine.getInstalledModels()
            res.fold(
                onSuccess = { info ->
                    _statusBannerMessage.value = "Imported & validated custom ONNX model (${info.sha256Short})"
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "Invalid ONNX model file: ${err.localizedMessage}"
                }
            )
        }
    }

    fun resetModelToBundled(task: LocalAiModelTask) {
        viewModelScope.launch(Dispatchers.IO) {
            _installedModels.value = onnxAiEngine.resetToBundledModel(task)
            _statusBannerMessage.value = "Restored bundled offline ONNX model for ${task.title}."
        }
    }

    // Project History Actions
    fun renameProject(id: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            projectRepository.renameProject(id, newName.trim())
        }
    }

    fun deleteProjectRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            projectRepository.deleteProject(id)
            _statusBannerMessage.value = "Project entry removed from history (original video preserved)."
        }
    }

    fun reopenProject(project: VideoProjectEntity) {
        val targetUri = project.exportedUriString ?: project.sourceUriString
        importVideoUri(Uri.parse(targetUri), navigateToAnalysis = true)
    }

    fun cleanupTemporaryCacheFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val exportTemp = File(context.cacheDir, "videox_exports")
            var freedBytes = 0L
            exportTemp.listFiles()?.forEach { f ->
                freedBytes += f.length()
                f.delete()
            }
            val mb = String.format("%.2f", freedBytes / (1024.0 * 1024.0))
            _statusBannerMessage.value = "Cleaned temporary processing buffers ($mb MB freed)."
        }
    }

    // Settings Actions
    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch { prefsRepository.setDarkTheme(isDark) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { prefsRepository.setLanguage(language) }
    }

    fun setProcessingProfile(profile: ProcessingProfile) {
        viewModelScope.launch {
            prefsRepository.setProcessingProfile(profile)
            _pipeline.update {
                it.copy(upscale = it.upscale.copy(tileSize = profile.defaultTileSize))
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            prefsRepository.setOnboardingCompleted(true)
            _currentScreen.value = StudioScreen.HOME
        }
    }

    fun exportInstalledApkToDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val res = apkExportHelper.saveInstalledApkToDownloads()
            res.fold(
                onSuccess = { saved ->
                    val sizeMb = String.format("%.1f MB", saved.sizeBytes / (1024.0 * 1024.0))
                    _statusBannerMessage.value = "APK Saved! File: ${saved.displayPath} ($sizeMb)"
                },
                onFailure = { err ->
                    _statusBannerMessage.value = "APK Export failed: ${err.localizedMessage}"
                }
            )
        }
    }
}
