package com.example.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.common.BilingualStrings
import com.example.core.common.VideoMetadata
import com.example.core.gpu.DeviceCapabilitiesReport
import com.example.data.database.VideoProjectEntity
import com.example.feature.StudioScreen
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess

@Composable
fun OnboardingScreen(
    strings: BilingualStrings,
    onContinue: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .testTag("onboarding_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.ic_studio_hero),
                        contentDescription = "VideoX AI Studio Banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.tagline,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = strings.privacyGuaranteeBody,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            OnboardingFeatureRow(
                icon = Icons.Default.HighQuality,
                title = "Tiled Local ONNX Super Resolution (Up to 4K UHD)",
                desc = "Processes frames in memory-safe overlapping tiles using ONNX Runtime Mobile with zero cloud uploads."
            )
        }
        item {
            OnboardingFeatureRow(
                icon = Icons.Default.ColorLens,
                title = "14-Axis Auto Color & Temporal Flicker Guard",
                desc = "Analyzes frame histograms for automatic exposure & white balance while protecting natural skin tones."
            )
        }
        item {
            OnboardingFeatureRow(
                icon = Icons.Default.SlowMotionVideo,
                title = "Optical Flow + RIFE Frame Interpolation & Stabilization",
                desc = "Converts 24/30 FPS to 60/120 FPS with scene-cut detection and smooths handheld camera shake."
            )
        }

        item {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_continue_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Enter VideoX AI Studio", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun OnboardingFeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ElectricPurple.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = ElectricPurple)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun HomeDashboardScreen(
    strings: BilingualStrings,
    currentVideo: VideoMetadata?,
    recentProjects: List<VideoProjectEntity>,
    deviceCapabilities: DeviceCapabilitiesReport?,
    isGeneratingSample: Boolean,
    onPickFromGallery: () -> Unit,
    onPickFromStorageSaf: () -> Unit,
    onGenerateTestVideo: () -> Unit,
    onNavigate: (StudioScreen) -> Unit,
    onOpenProject: (VideoProjectEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_dashboard_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Studio Header Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box {
                    Image(
                        painter = painterResource(id = R.drawable.ic_studio_hero),
                        contentDescription = "Studio Visual Header",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EmeraldSuccess.copy(alpha = 0.18f),
                            modifier = Modifier.border(1.dp, EmeraldSuccess.copy(alpha = 0.5f), RoundedCornerShape(50))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.offlineBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldSuccess
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(64.dp))
                        Text(
                            text = strings.appName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = strings.tagline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.importVideoTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPickFromGallery,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_import_photo_picker"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.importFromPhotoPicker, style = MaterialTheme.typography.labelLarge)
                        }

                        OutlinedButton(
                            onClick = onPickFromStorageSaf,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("btn_import_saf"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.importFromStorage, style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    OutlinedButton(
                        onClick = onGenerateTestVideo,
                        enabled = !isGeneratingSample,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_generate_sample_video"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isGeneratingSample) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synthesizing MP4 Test Stream via MediaCodec...")
                        } else {
                            Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.generateTestVideo)
                        }
                    }
                }
            }
        }

        // Active Loaded Video Card (if user imported or generated a video)
        if (currentVideo != null) {
            item {
                ActiveVideoBannerCard(
                    video = currentVideo,
                    onAnalyze = { onNavigate(StudioScreen.VIDEO_ANALYSIS) },
                    onEdit = { onNavigate(StudioScreen.EDITOR) },
                    onExport = { onNavigate(StudioScreen.EXPORT_SETTINGS) }
                )
            }
        }

        // Hardware & Thermal Readiness Strip
        if (deviceCapabilities != null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate(StudioScreen.DEVICE_COMPATIBILITY) }
                        .testTag("card_device_telemetry")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = CyberBlue)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "${deviceCapabilities.deviceModel} • ${deviceCapabilities.availableRamMb}MB Free RAM",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Profile: ${deviceCapabilities.recommendedProfile.label} • Thermal: ${deviceCapabilities.thermalStatusLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Quick Studio Modules Grid
        item {
            Text(
                text = strings.quickToolsTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        item {
            StudioModuleGrid(strings = strings, onNavigate = onNavigate)
        }

        // Recent Projects Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(strings.recentProjectsTitle, style = MaterialTheme.typography.titleLarge)
                if (recentProjects.isNotEmpty()) {
                    Text(
                        text = "View All (${recentProjects.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricPurple,
                        modifier = Modifier.clickable { onNavigate(StudioScreen.PROJECTS) }
                    )
                }
            }
        }

        if (recentProjects.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.noProjectsYet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentProjects.take(3), key = { it.id }) { project ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(project) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.projectName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "${project.sourceResolution} → ${project.targetResolution} • ${project.upscaleModeLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (project.status == "COMPLETED") EmeraldSuccess.copy(alpha = 0.2f) else ElectricPurple.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = project.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (project.status == "COMPLETED") EmeraldSuccess else ElectricPurple,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveVideoBannerCard(
    video: VideoMetadata,
    onAnalyze: () -> Unit,
    onEdit: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ElectricPurple.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .testTag("active_video_card")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ACTIVE STREAM LOADED",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricPurple
                    )
                    Text(
                        text = video.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberBlue.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${video.resolutionBadge} • ${video.frameRate.toInt()} FPS",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAnalyze,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_open_analysis"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Stream Info")
                }
                Button(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_open_editor"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Studio Editor")
                }
                Button(
                    onClick = onExport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_open_export"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Export")
                }
            }
        }
    }
}

data class ToolCardSpec(
    val title: String,
    val badge: String,
    val icon: ImageVector,
    val accent: Color,
    val screen: StudioScreen,
    val tag: String
)

@Composable
fun StudioModuleGrid(
    strings: BilingualStrings,
    onNavigate: (StudioScreen) -> Unit
) {
    val tools = listOf(
        ToolCardSpec(strings.toolAiUpscaler, "ONNX 4K SR", Icons.Default.HighQuality, ElectricPurple, StudioScreen.AI_UPSCALER, "tool_card_upscaler"),
        ToolCardSpec(strings.toolAutoColor, "14-Axis Grading", Icons.Default.ColorLens, CyberBlue, StudioScreen.AUTO_COLOR, "tool_card_color"),
        ToolCardSpec(strings.toolFpsConverter, "24..120 FPS + RIFE", Icons.Default.SlowMotionVideo, ElectricPurple, StudioScreen.FPS_CONVERTER, "tool_card_fps"),
        ToolCardSpec(strings.toolStabilizer, "Motion Trajectory", Icons.Default.CenterFocusStrong, CyberBlue, StudioScreen.STABILIZER, "tool_card_stabilizer"),
        ToolCardSpec(strings.toolDenoiser, "Spatial-Temporal AI", Icons.Default.BlurOn, ElectricPurple, StudioScreen.DENOISER, "tool_card_denoiser"),
        ToolCardSpec(strings.toolSharpen, "Edge & Clarity", Icons.Default.AutoAwesome, CyberBlue, StudioScreen.SHARPEN_DETAIL, "tool_card_sharpen"),
        ToolCardSpec(strings.toolModelManager, "3 Bundled .ONNX", Icons.Default.Tune, EmeraldSuccess, StudioScreen.AI_MODEL_MANAGER, "tool_card_models"),
        ToolCardSpec(strings.toolDeviceAnalyzer, "Codecs & Thermal", Icons.Default.Memory, CyberBlue, StudioScreen.DEVICE_COMPATIBILITY, "tool_card_hardware")
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (pair in tools.chunked(2)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (tool in pair) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigate(tool.screen) }
                            .testTag(tool.tag)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(tool.accent.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(tool.icon, contentDescription = tool.title, tint = tool.accent)
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = tool.accent.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = tool.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = tool.accent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Text(
                                text = tool.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsHubScreen(
    strings: BilingualStrings,
    onNavigate: (StudioScreen) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tools_hub_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = strings.quickToolsTitle,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Every module processes frames locally on-device without cloud APIs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            StudioModuleGrid(strings = strings, onNavigate = onNavigate)
        }
    }
}
