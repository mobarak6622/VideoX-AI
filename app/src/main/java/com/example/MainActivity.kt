package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.ai.LocalAiModelTask
import com.example.core.common.AppLanguage
import com.example.core.common.AppStrings
import com.example.feature.StudioScreen
import com.example.feature.StudioViewModel
import com.example.feature.editor.EditXStudioScreen
import com.example.feature.export.ExportSettingsScreen
import com.example.feature.export.ExportSuccessScreen
import com.example.feature.export.ProcessingScreen
import com.example.feature.home.HomeDashboardScreen
import com.example.feature.home.OnboardingScreen
import com.example.feature.home.ToolsHubScreen
import com.example.feature.importer.VideoAnalysisScreen
import com.example.feature.importer.VideoEditorScreen
import com.example.feature.importer.VideoPreviewScreen
import com.example.feature.profile.ProfileAndProScreen
import com.example.feature.projects.AiModelManagerScreen
import com.example.feature.projects.DeviceCompatibilityScreen
import com.example.feature.projects.PrivacyInfoScreen
import com.example.feature.projects.ProjectHistoryScreen
import com.example.feature.projects.QueueManagerScreen
import com.example.feature.projects.SettingsScreen
import com.example.feature.templates.TemplatesScreen
import com.example.feature.upscaler.AiUpscalerScreen
import com.example.feature.upscaler.AutoColorScreen
import com.example.feature.upscaler.DenoiserScreen
import com.example.feature.upscaler.DslrBokehScreen
import com.example.feature.upscaler.FpsConverterScreen
import com.example.feature.upscaler.SharpenDetailScreen
import com.example.feature.upscaler.StabilizerScreen
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.VideoXAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Configure Firebase App Check with launch intent before UI or Firebase operations
        com.example.core.ai.configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            val studioViewModel: StudioViewModel = viewModel()
            val userSettings by studioViewModel.userSettings.collectAsStateWithLifecycle()

            VideoXAITheme(darkTheme = userSettings.isDarkTheme) {
                VideoXStudioApp(viewModel = studioViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoXStudioApp(viewModel: StudioViewModel) {
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val strings = AppStrings.get(userSettings.language)

    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val currentVideo by viewModel.currentVideo.collectAsStateWithLifecycle()
    val pipeline by viewModel.pipeline.collectAsStateWithLifecycle()
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val deviceCapabilities by viewModel.deviceCapabilities.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val processingState by viewModel.processingState.collectAsStateWithLifecycle()
    val originalFrame by viewModel.originalPreviewFrame.collectAsStateWithLifecycle()
    val enhancedFrame by viewModel.enhancedPreviewFrame.collectAsStateWithLifecycle()
    val previewTimestampMs by viewModel.previewTimestampMs.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isGeneratingSample by viewModel.isGeneratingSample.collectAsStateWithLifecycle()
    val queueTasks by viewModel.queueTasks.collectAsStateWithLifecycle()
    val lastProcessedVideoUri by viewModel.lastProcessedVideoUri.collectAsStateWithLifecycle()

    var pendingModelTaskForImport by remember { mutableStateOf<LocalAiModelTask?>(null) }

    // Zero-Permission Android Photo Picker for Video Import
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importVideoUri(uri, navigateToAnalysis = true)
        }
    }

    // Storage Access Framework (SAF) for Video Import
    val safVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                viewModel.getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importVideoUri(uri, navigateToAnalysis = true)
        }
    }

    // Storage Access Framework (SAF) for Custom Local .ONNX Model Import
    val safOnnxModelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val task = pendingModelTaskForImport
        if (uri != null && task != null) {
            viewModel.importCustomOnnxModel(task, uri)
        }
        pendingModelTaskForImport = null
    }

    // Handle system Back navigation on all secondary screens
    BackHandler(enabled = currentScreen != StudioScreen.HOME) {
        viewModel.navigateBack()
    }

    val isRootTab = currentScreen in setOf(
        StudioScreen.HOME,
        StudioScreen.TEMPLATES,
        StudioScreen.PROJECTS,
        StudioScreen.PROFILE,
        StudioScreen.SETTINGS
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("top_bar_app_title")
                    )
                },
                navigationIcon = {
                    if (!isRootTab) {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.testTag("btn_nav_back")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // Quick Bilingual Toggle (EN / বাংলা)
                    IconButton(
                        onClick = {
                            val nextLang = if (userSettings.language == AppLanguage.ENGLISH) {
                                AppLanguage.BENGALI
                            } else {
                                AppLanguage.ENGLISH
                            }
                            viewModel.setLanguage(nextLang)
                        },
                        modifier = Modifier.testTag("btn_toggle_language")
                    ) {
                        Icon(Icons.Default.Language, contentDescription = "Switch Language")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_navigation_bar")
            ) {
                // Tab 1: Home
                NavigationBarItem(
                    selected = currentScreen == StudioScreen.HOME,
                    onClick = { viewModel.navigateTo(StudioScreen.HOME) },
                    icon = {
                        Icon(
                            if (currentScreen == StudioScreen.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = strings.navHome
                        )
                    },
                    label = { Text(strings.navHome, fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_tab_home")
                )

                // Tab 2: Templates
                NavigationBarItem(
                    selected = currentScreen == StudioScreen.TEMPLATES,
                    onClick = { viewModel.navigateTo(StudioScreen.TEMPLATES) },
                    icon = {
                        Icon(
                            if (currentScreen == StudioScreen.TEMPLATES) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                            contentDescription = strings.navTemplates
                        )
                    },
                    label = { Text(strings.navTemplates, fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_tab_templates")
                )

                // Tab 3: Create (Large central prominent action button)
                NavigationBarItem(
                    selected = currentScreen == StudioScreen.EDITOR,
                    onClick = {
                        if (currentVideo != null) {
                            viewModel.navigateTo(StudioScreen.EDITOR)
                        } else {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    },
                    icon = {
                        Surface(
                            shape = CircleShape,
                            color = ElectricPurple,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, contentDescription = strings.navCreate, tint = androidx.compose.ui.graphics.Color.White)
                            }
                        }
                    },
                    label = { Text(strings.navCreate, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ElectricPurple) },
                    modifier = Modifier.testTag("nav_tab_create")
                )

                // Tab 4: Projects
                NavigationBarItem(
                    selected = currentScreen == StudioScreen.PROJECTS,
                    onClick = { viewModel.navigateTo(StudioScreen.PROJECTS) },
                    icon = {
                        Icon(
                            if (currentScreen == StudioScreen.PROJECTS) Icons.Filled.FolderSpecial else Icons.Outlined.FolderSpecial,
                            contentDescription = strings.navProjects
                        )
                    },
                    label = { Text(strings.navProjects, fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_tab_projects")
                )

                // Tab 5: Profile & Pro
                NavigationBarItem(
                    selected = currentScreen == StudioScreen.PROFILE,
                    onClick = { viewModel.navigateTo(StudioScreen.PROFILE) },
                    icon = {
                        Icon(
                            if (currentScreen == StudioScreen.PROFILE) Icons.Filled.Person else Icons.Outlined.Person,
                            contentDescription = strings.navProfile
                        )
                    },
                    label = { Text(strings.navProfile, fontSize = 11.sp) },
                    modifier = Modifier.testTag("nav_tab_profile")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (statusBanner != null) {
                Surface(
                    color = ElectricPurple.copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("status_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusBanner ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.dismissBanner() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    StudioScreen.ONBOARDING -> OnboardingScreen(
                        strings = strings,
                        onContinue = { viewModel.completeOnboarding() }
                    )

                    StudioScreen.HOME -> HomeDashboardScreen(
                        strings = strings,
                        currentVideo = currentVideo,
                        recentProjects = projects,
                        deviceCapabilities = deviceCapabilities,
                        isGeneratingSample = isGeneratingSample,
                        onPickFromGallery = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        onPickFromStorageSaf = {
                            safVideoLauncher.launch(arrayOf("video/*"))
                        },
                        onGenerateTestVideo = {
                            viewModel.generateAndImportSampleVideo()
                        },
                        onNavigate = { viewModel.navigateTo(it) },
                        onOpenProject = { viewModel.reopenProject(it) },
                        onDownloadApk = { viewModel.exportInstalledApkToDownloads() }
                    )

                    StudioScreen.VIDEO_ANALYSIS -> VideoAnalysisScreen(
                        strings = strings,
                        metadata = currentVideo,
                        onOpenEditor = { viewModel.navigateTo(StudioScreen.EDITOR) },
                        onOpenUpscaler = { viewModel.navigateTo(StudioScreen.AI_UPSCALER) },
                        onOpenExport = { viewModel.navigateTo(StudioScreen.EXPORT_SETTINGS) },
                        onPickAnotherVideo = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )

                    StudioScreen.VIDEO_PREVIEW -> VideoPreviewScreen(
                        metadata = currentVideo,
                        processedVideoUriString = lastProcessedVideoUri,
                        pipeline = pipeline,
                        onNavigateToEditor = { viewModel.navigateTo(StudioScreen.EDITOR) },
                        onNavigateToExport = { viewModel.navigateTo(StudioScreen.EXPORT_SETTINGS) },
                        onQueueBackgroundTask = { viewModel.queueCurrentVideoForEnhancement() }
                    )

                    StudioScreen.EDITOR -> EditXStudioScreen(
                        strings = strings,
                        metadata = currentVideo,
                        pipeline = pipeline,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = { viewModel.undo() },
                        onRedo = { viewModel.redo() },
                        onExportVideo = { viewModel.navigateTo(StudioScreen.EXPORT_SETTINGS) },
                        onUpdateColorConfig = { viewModel.updateColorConfig(it) },
                        onOneTapAutoColor = { viewModel.applyOneTapAutoColor() },
                        onOpenAiUpscaler = { viewModel.navigateTo(StudioScreen.AI_UPSCALER) },
                        onNavigateBack = { viewModel.navigateBack() }
                    )

                    StudioScreen.TEMPLATES -> TemplatesScreen(
                        strings = strings,
                        onUseTemplate = { template ->
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    )

                    StudioScreen.PROFILE -> ProfileAndProScreen(
                        strings = strings,
                        projectCount = projects.size,
                        onNavigateToSettings = { viewModel.navigateTo(StudioScreen.SETTINGS) },
                        onNavigateToPrivacy = { viewModel.navigateTo(StudioScreen.PRIVACY_INFO) }
                    )

                    StudioScreen.AI_UPSCALER -> AiUpscalerScreen(
                        config = pipeline.upscale,
                        installedModels = installedModels,
                        originalFrame = originalFrame,
                        enhancedFrame = enhancedFrame,
                        onUpdate = { viewModel.updateUpscaleConfig(it) },
                        onProceedToExport = { viewModel.navigateTo(StudioScreen.EXPORT_SETTINGS) }
                    )

                    StudioScreen.AUTO_COLOR -> AutoColorScreen(
                        config = pipeline.color,
                        originalFrame = originalFrame,
                        enhancedFrame = enhancedFrame,
                        onApplyOneTapAuto = { viewModel.applyOneTapAutoColor() },
                        onUpdate = { viewModel.updateColorConfig(it) }
                    )

                    StudioScreen.FPS_CONVERTER -> FpsConverterScreen(
                        config = pipeline.fps,
                        onUpdate = { viewModel.updateFpsConfig(it) }
                    )

                    StudioScreen.STABILIZER -> StabilizerScreen(
                        config = pipeline.stabilize,
                        onUpdate = { viewModel.updateStabilizeConfig(it) }
                    )

                    StudioScreen.DENOISER -> DenoiserScreen(
                        config = pipeline.denoise,
                        onUpdate = { viewModel.updateDenoiseConfig(it) }
                    )

                    StudioScreen.SHARPEN_DETAIL -> SharpenDetailScreen(
                        config = pipeline.sharpen,
                        onUpdate = { viewModel.updateSharpenConfig(it) }
                    )

                    StudioScreen.DSLR_BOKEH -> DslrBokehScreen(
                        config = pipeline.bokeh,
                        originalFrame = originalFrame,
                        enhancedFrame = enhancedFrame,
                        onUpdate = { viewModel.updateBokehConfig(it) },
                        onApplyAiDirector = { viewModel.applyGeminiCapCutProDirector() }
                    )

                    StudioScreen.EXPORT_SETTINGS -> ExportSettingsScreen(
                        metadata = currentVideo,
                        pipeline = pipeline,
                        capabilities = deviceCapabilities,
                        estimatedBitrateAndSize = viewModel.estimateExportSizeAndBitrate(),
                        onUpdateExport = { viewModel.updateExportConfig(it) },
                        onStartRealExport = { viewModel.startRealExportProcessing(resumeFromCheckpoint = false) }
                    )

                    StudioScreen.PROCESSING -> ProcessingScreen(
                        state = processingState,
                        onTogglePause = { viewModel.togglePauseProcessing() },
                        onCancel = { viewModel.cancelProcessing() },
                        onResumeFromCheckpoint = { viewModel.startRealExportProcessing(resumeFromCheckpoint = true) }
                    )

                    StudioScreen.EXPORT_SUCCESS -> ExportSuccessScreen(
                        state = processingState,
                        onOpenExportedVideo = { uriStr ->
                            viewModel.importVideoUri(android.net.Uri.parse(uriStr), navigateToAnalysis = true)
                        },
                        onBackHome = { viewModel.navigateTo(StudioScreen.HOME) }
                    )

                    StudioScreen.PROJECTS -> ProjectHistoryScreen(
                        strings = strings,
                        projects = projects,
                        onReopenProject = { viewModel.reopenProject(it) },
                        onRenameProject = { id, name -> viewModel.renameProject(id, name) },
                        onDeleteProject = { id -> viewModel.deleteProjectRecord(id) },
                        onCleanTempFiles = { viewModel.cleanupTemporaryCacheFiles() }
                    )

                    StudioScreen.TOOLS_HUB -> ToolsHubScreen(
                        strings = strings,
                        onNavigate = { viewModel.navigateTo(it) }
                    )

                    StudioScreen.AI_MODEL_MANAGER -> AiModelManagerScreen(
                        models = installedModels,
                        onBenchmarkModel = { viewModel.benchmarkAiModel(it) },
                        onImportCustomModel = { task ->
                            pendingModelTaskForImport = task
                            safOnnxModelLauncher.launch(arrayOf("*/*"))
                        },
                        onResetBundledModel = { viewModel.resetModelToBundled(it) }
                    )

                    StudioScreen.QUEUE_MANAGER -> QueueManagerScreen(
                        tasks = queueTasks,
                        onCancelTask = { viewModel.cancelQueueTask(it) },
                        onRemoveTask = { viewModel.removeQueueTask(it) },
                        onClearCompleted = { viewModel.clearCompletedQueueTasks() },
                        onOpenVideoPreview = { uriStr ->
                            viewModel.importVideoUri(android.net.Uri.parse(uriStr), navigateToAnalysis = false)
                            viewModel.navigateTo(StudioScreen.VIDEO_PREVIEW)
                        },
                        onNavigateBack = { viewModel.navigateBack() }
                    )

                    StudioScreen.DEVICE_COMPATIBILITY -> DeviceCompatibilityScreen(
                        report = deviceCapabilities,
                        onRefresh = { viewModel.refreshHardwareAndModels() }
                    )

                    StudioScreen.PRIVACY_INFO -> PrivacyInfoScreen(strings = strings)

                    StudioScreen.SETTINGS -> SettingsScreen(
                        strings = strings,
                        settings = userSettings,
                        onToggleDarkTheme = { viewModel.setDarkTheme(it) },
                        onChangeLanguage = { viewModel.setLanguage(it) },
                        onChangeProfile = { viewModel.setProcessingProfile(it) },
                        onChangePreviewQuality = { viewModel.updatePreviewQuality(it) },
                        onNavigate = { viewModel.navigateTo(it) },
                        onCleanCache = { viewModel.exportInstalledApkToDownloads() }
                    )
                }
            }
        }
    }
}
