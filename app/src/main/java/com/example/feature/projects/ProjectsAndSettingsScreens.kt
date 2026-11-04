package com.example.feature.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.core.ai.InstalledAiModelInfo
import com.example.core.ai.LocalAiModelTask
import com.example.core.common.AppLanguage
import com.example.core.common.BilingualStrings
import com.example.core.common.PreviewQuality
import com.example.core.common.ProcessingProfile
import com.example.core.gpu.DeviceCapabilitiesReport
import com.example.data.database.VideoProjectEntity
import com.example.data.preferences.UserSettingsState
import com.example.feature.StudioScreen
import com.example.feature.importer.formatDurationMs
import com.example.feature.importer.formatFileSize
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess

@Composable
fun ProjectHistoryScreen(
    strings: BilingualStrings,
    projects: List<VideoProjectEntity>,
    onReopenProject: (VideoProjectEntity) -> Unit,
    onRenameProject: (Long, String) -> Unit,
    onDeleteProject: (Long) -> Unit,
    onCleanTempFiles: () -> Unit
) {
    var projectToRename by remember { mutableStateOf<VideoProjectEntity?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (projectToRename != null) {
        AlertDialog(
            onDismissRequest = { projectToRename = null },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Project Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        projectToRename?.let { onRenameProject(it.id, renameText) }
                        projectToRename = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { projectToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("project_history_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.recentProjectsTitle, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Stored locally in Room SQLite Database. Original videos are never deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onCleanTempFiles,
                    modifier = Modifier.testTag("btn_clean_temp_files")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clean Cache")
                }
            }
        }

        if (projects.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(strings.noProjectsYet, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        } else {
            items(projects, key = { it.id }) { proj ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(proj.projectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(proj.status, style = MaterialTheme.typography.labelMedium, color = EmeraldSuccess)
                        }
                        Text(
                            "${proj.sourceResolution} → ${proj.targetResolution} • ${proj.targetFps} FPS • ${proj.codecLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Mode: ${proj.upscaleModeLabel} • Duration: ${formatDurationMs(proj.durationMs)} • Size: ${formatFileSize(proj.outputFileSizeBytes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberBlue
                        )
                        if (proj.exportedDisplayPath != null) {
                            Text("Saved: ${proj.exportedDisplayPath}", style = MaterialTheme.typography.labelSmall)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onReopenProject(proj) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open / Re-Export")
                            }
                            IconButton(
                                onClick = {
                                    renameText = proj.projectName
                                    projectToRename = proj
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename")
                            }
                            IconButton(onClick = { onDeleteProject(proj.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Record")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiModelManagerScreen(
    models: List<InstalledAiModelInfo>,
    onBenchmarkModel: (LocalAiModelTask) -> Unit,
    onImportCustomModel: (LocalAiModelTask) -> Unit,
    onResetBundledModel: (LocalAiModelTask) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_model_manager_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Local ONNX AI Model Manager", style = MaterialTheme.typography.headlineMedium)
            Text(
                "All neural network models run 100% offline via ONNX Runtime Mobile. You can benchmark bundled models or import custom .onnx files (e.g. Real-ESRGAN / RIFE) from device storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(models, key = { it.task.id }) { info ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(info.task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(info.task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(info.task.architectureLabel, style = MaterialTheme.typography.labelMedium, color = ElectricPurple)
                    Text(
                        "Hardware Accelerator: ${info.activeExecutionProvider}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberBlue
                    )
                    Text(
                        "Status: ${info.statusMessage} • SHA-256: ${info.sha256Short} • ${formatFileSize(info.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSuccess
                    )
                    if (info.lastBenchmarkMs != null) {
                        Text(
                            "Verified On-Device Inference Latency: ${info.lastBenchmarkMs} ms / 64×64 tile",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyberBlue
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onBenchmarkModel(info.task) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_benchmark_${info.task.id}")
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Benchmark")
                        }
                        OutlinedButton(
                            onClick = { onImportCustomModel(info.task) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import .onnx")
                        }
                        if (info.isCustomUserModel) {
                            IconButton(onClick = { onResetBundledModel(info.task) }) {
                                Icon(Icons.Default.Restore, contentDescription = "Restore Bundled")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCompatibilityScreen(
    report: DeviceCapabilitiesReport?,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("device_compatibility_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hardware & Codec Analyzer", style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = onRefresh) {
                    Text("Re-Scan")
                }
            }
        }

        if (report != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "GPU / NPU / VPU Hardware Acceleration Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                        Text(
                            "AI Neural Engine: ${report.hardwareAcceleration.activeAiDelegateName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricPurple
                        )
                        Text(
                            "GPU Shader Pipeline: ${report.hardwareAcceleration.activeGpuPipelineName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CyberBlue
                        )
                        Text("Hardware Video Decoder (VPU): ${report.hardwareAcceleration.primaryHardwareDecoderName}")
                        Text("Hardware Video Encoder (VPU): ${report.hardwareAcceleration.primaryHardwareEncoderName}")
                        Text(
                            "Verification Summary: ${report.hardwareAcceleration.summaryBadge}",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldSuccess
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("System & Memory Telemetry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Device: ${report.deviceModel} (${report.androidVersionRelease}, API ${report.sdkInt})")
                        Text("SoC / Hardware: ${report.socHardware} • ${report.cpuCores} CPU Cores (${report.supportedAbis})")
                        Text("Memory: ${report.availableRamMb} MB Free / ${report.totalRamMb} MB Total RAM")
                        Text("Available Storage: ${report.availableStorageMb} MB")
                        Text("GPU Graphics: OpenGL ES ${report.glEsVersion} • Vulkan Support: ${if (report.supportsVulkan) "Yes" else "Fallback to GLES/CPU"}")
                        Text("Thermal Status: ${report.thermalStatusLabel}", color = EmeraldSuccess)
                        Text("Recommended Profile: ${report.recommendedProfile.label} (${report.recommendedTileSize}px AI Tiles)", color = ElectricPurple)
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("4K UHD & Hardware Encoder Matrix", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        report.codecSupportMap.values.forEach { support ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    "${support.codecOption.label} • 4K UHD: ${if (support.supports4kUhd) "Supported" else "Max ${support.maxSupportedWidth}×${support.maxSupportedHeight}"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (support.supports4kUhd) EmeraldSuccess else CyberBlue
                                )
                                Text(
                                    "Encoders: ${support.encoderNames.joinToString().ifBlank { "None" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    strings: BilingualStrings,
    settings: UserSettingsState,
    onToggleDarkTheme: (Boolean) -> Unit,
    onChangeLanguage: (AppLanguage) -> Unit,
    onChangeProfile: (ProcessingProfile) -> Unit,
    onChangePreviewQuality: (PreviewQuality) -> Unit,
    onNavigate: (StudioScreen) -> Unit,
    onCleanCache: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.navSettings, style = MaterialTheme.typography.headlineMedium)
        }

        // Theme & Language Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Premium Dark Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Obsidian #0B0D14 with Electric Purple & Blue accents", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.isDarkTheme,
                            onCheckedChange = onToggleDarkTheme,
                            modifier = Modifier.testTag("switch_dark_theme")
                        )
                    }

                    Text("Application Language (English / বাংলা)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = settings.language == lang,
                                onClick = { onChangeLanguage(lang) },
                                label = { Text("${lang.displayName} (${lang.nativeName})") },
                                modifier = Modifier.testTag("chip_lang_${lang.code}")
                            )
                        }
                    }
                }
            }
        }

        // Adaptive Processing Profile Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Hardware Processing Profile", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ProcessingProfile.entries.forEach { profile ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = settings.processingProfile == profile,
                                onClick = { onChangeProfile(profile) },
                                label = { Text(profile.label) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(profile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text("Default Editor Preview Resolution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewQuality.entries.forEach { pq ->
                            FilterChip(
                                selected = settings.defaultPreviewQuality == pq,
                                onClick = { onChangePreviewQuality(pq) },
                                label = { Text(pq.label) }
                            )
                        }
                    }
                }
            }
        }

        // Quick Links to Model Manager, Device Analyzer, Privacy & Onboarding
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("System & Offline Verification", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = { onNavigate(StudioScreen.AI_MODEL_MANAGER) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Local ONNX AI Model Manager")
                    }
                    OutlinedButton(onClick = { onNavigate(StudioScreen.DEVICE_COMPATIBILITY) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Hardware & 4K Codec Compatibility")
                    }
                    OutlinedButton(onClick = { onNavigate(StudioScreen.PRIVACY_INFO) }, modifier = Modifier.fillMaxWidth().testTag("btn_open_privacy_info")) {
                        Text("View 100% Offline Privacy & Security Guarantee")
                    }
                    OutlinedButton(onClick = { onNavigate(StudioScreen.ONBOARDING) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Replay Studio Onboarding Guide")
                    }
                    Button(
                        onClick = onCleanCache,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_apk_downloads")
                    ) {
                        Text("Save Installed APK to Downloads (VideoX_AI_v1.0.0.apk)")
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyInfoScreen(strings: BilingualStrings) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("privacy_info_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.privacyGuaranteeTitle, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.privacyGuaranteeBody, style = MaterialTheme.typography.bodyLarge)
                    Text("• Zero Cloud Servers: No video frames or metadata are ever uploaded.", style = MaterialTheme.typography.bodyMedium)
                    Text("• No Internet Permission: android.permission.INTERNET is explicitly removed from AndroidManifest.xml.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Scoped Storage & Photo Picker: Uses Android Photo Picker and MediaStore without broad storage permissions.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Local ONNX AI Models: Bundled inside APK assets and executed locally via ONNX Runtime Mobile.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
