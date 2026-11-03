package com.example.feature.export

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.common.AudioExportMode
import com.example.core.common.BitratePreset
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.ExportConfig
import com.example.core.common.TargetResolution
import com.example.core.common.VideoCodecOption
import com.example.core.common.VideoMetadata
import com.example.core.gpu.DeviceCapabilitiesReport
import com.example.feature.ActiveProcessingState
import com.example.feature.importer.formatDurationMs
import com.example.feature.importer.formatFileSize
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportSettingsScreen(
    metadata: VideoMetadata?,
    pipeline: EnhancementPipelineState,
    capabilities: DeviceCapabilitiesReport?,
    estimatedBitrateAndSize: Pair<Int, Long>,
    onUpdateExport: (ExportConfig) -> Unit,
    onStartRealExport: () -> Unit
) {
    val exportCfg = pipeline.export
    val effectiveRes = if (exportCfg.resolution != TargetResolution.ORIGINAL) {
        exportCfg.resolution
    } else {
        pipeline.upscale.targetResolution
    }
    val effectiveFps = when {
        exportCfg.targetFps > 0 -> exportCfg.targetFps
        pipeline.fps.targetFps > 0 -> pipeline.fps.targetFps
        else -> (metadata?.frameRate?.roundToInt() ?: 30)
    }

    val (isHwCompatible, hwMessage) = capabilities?.checkExportCompatibility(
        targetResolution = effectiveRes,
        codec = exportCfg.codec,
        targetFps = effectiveFps,
        inputWidth = metadata?.width ?: 1920,
        inputHeight = metadata?.height ?: 1080
    ) ?: Pair(true, "Hardware encoder ready")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("export_settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Professional MP4 Export Engine", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Hardware-verified MediaCodec encoding with MediaStore output to Movies/VideoX AI.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Pre-Export Telemetry & Hardware Verification Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = if (isHwCompatible) EmeraldSuccess.copy(alpha = 0.6f) else CrimsonError,
                    shape = RoundedCornerShape(20.dp)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pre-Export Verification Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val (outW, outH) = effectiveRes.computeOutputDimensions(metadata?.width ?: 1280, metadata?.height ?: 720)
                    Text("Target Resolution: ${effectiveRes.label} (${outW}×${outH})", style = MaterialTheme.typography.bodyMedium)
                    Text("Target Frame Rate: $effectiveFps FPS", style = MaterialTheme.typography.bodyMedium)
                    Text("Video Codec: ${exportCfg.codec.label} (.MP4 Container)", style = MaterialTheme.typography.bodyMedium)
                    Text("Audio Stream: ${exportCfg.audioMode.label}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Estimated Bitrate & Size: ${estimatedBitrateAndSize.first / 1_000_000} Mbps • ~${formatFileSize(estimatedBitrateAndSize.second)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyberBlue
                    )
                    Text(
                        text = hwMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isHwCompatible) EmeraldSuccess else CrimsonError
                    )
                }
            }
        }

        // Resolution Selection
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Export Resolution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TargetResolution.entries.forEach { res ->
                            FilterChip(
                                selected = exportCfg.resolution == res,
                                onClick = { onUpdateExport(exportCfg.copy(resolution = res)) },
                                label = { Text(res.shortBadge) },
                                modifier = Modifier.testTag("export_res_${res.name}")
                            )
                        }
                    }
                }
            }
        }

        // Codec & FPS Selection
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Video Codec", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    VideoCodecOption.entries.forEach { codec ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = exportCfg.codec == codec,
                                onClick = { onUpdateExport(exportCfg.copy(codec = codec)) }
                            )
                            Text(codec.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Text("Export Frame Rate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 24, 25, 30, 50, 60, 120).forEach { fps ->
                            FilterChip(
                                selected = exportCfg.targetFps == fps,
                                onClick = { onUpdateExport(exportCfg.copy(targetFps = fps)) },
                                label = { Text(if (fps == 0) "Original" else "${fps}fps") }
                            )
                        }
                    }
                }
            }
        }

        // Bitrate & Audio Configuration
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Bitrate Quality Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BitratePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = exportCfg.bitratePreset == preset,
                                onClick = { onUpdateExport(exportCfg.copy(bitratePreset = preset)) },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    if (exportCfg.bitratePreset == BitratePreset.CUSTOM) {
                        Text("Custom Target Bitrate: ${exportCfg.customBitrateMbps.roundToInt()} Mbps")
                        Slider(
                            value = exportCfg.customBitrateMbps,
                            onValueChange = { onUpdateExport(exportCfg.copy(customBitrateMbps = it)) },
                            valueRange = 2f..60f
                        )
                    }

                    Text("Audio Track Configuration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    AudioExportMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = exportCfg.audioMode == mode,
                                onClick = { onUpdateExport(exportCfg.copy(audioMode = mode)) }
                            )
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onStartRealExport,
                enabled = metadata != null && metadata.isSupported && isHwCompatible,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("btn_start_real_export"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
            ) {
                Text("Render & Save MP4 to Movies/VideoX AI", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ProcessingScreen(
    state: ActiveProcessingState,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onResumeFromCheckpoint: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("processing_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("On-Device Frame Processing Queue", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Real hardware MediaCodec & ONNX Runtime execution — never fake progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(state.stageName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            "${state.progressPercent}%",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ElectricPurple,
                            modifier = Modifier.testTag("processing_percent_text")
                        )
                    }

                    LinearProgressIndicator(
                        progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = ElectricPurple
                    )

                    Text(state.activePipelineNote, style = MaterialTheme.typography.bodySmall, color = CyberBlue)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Frames: ${state.completedFrames} / ${state.totalFrames}", style = MaterialTheme.typography.labelMedium)
                        Text(String.format("Speed: %.1f fps", state.currentFpsSpeed), style = MaterialTheme.typography.labelMedium)
                        Text("ETA: ${state.estimatedRemainingSeconds}s", style = MaterialTheme.typography.labelMedium)
                    }

                    if (state.errorMessage != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AmberWarning)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(state.errorMessage, style = MaterialTheme.typography.bodySmall, color = AmberWarning)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.isRunning) {
                            OutlinedButton(
                                onClick = onTogglePause,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_pause_resume_processing")
                            ) {
                                Icon(
                                    if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (state.isPaused) "Resume" else "Pause")
                            }

                            Button(
                                onClick = onCancel,
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonError),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_cancel_processing")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel")
                            }
                        } else if (state.lastCheckpointFrame > 0 && state.completedResult == null) {
                            Button(
                                onClick = onResumeFromCheckpoint,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_checkpoint_recover")
                            ) {
                                Text("Resume from Checkpoint (Frame #${state.lastCheckpointFrame})")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExportSuccessScreen(
    state: ActiveProcessingState,
    onOpenExportedVideo: (String) -> Unit,
    onBackHome: () -> Unit
) {
    val context = LocalContext.current
    val result = state.completedResult

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("export_success_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.border(1.dp, EmeraldSuccess, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Verified Success",
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "Export Verified & Saved to Gallery",
                        style = MaterialTheme.typography.headlineMedium,
                        color = EmeraldSuccess
                    )
                    if (result != null) {
                        Text(
                            text = "Location: ${result.displayPath}",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Verified Stream: ${result.verifiedWidth}×${result.verifiedHeight} • ${formatDurationMs(result.verifiedDurationMs)} • ${formatFileSize(result.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = { onOpenExportedVideo(result.galleryUri.toString()) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("btn_reopen_exported_video"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Reopen & Verify Exported Video in Studio")
                        }

                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, result.galleryUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Enhanced Video"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share MP4 Video")
                        }
                    }

                    OutlinedButton(
                        onClick = onBackHome,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Return to Studio Dashboard")
                    }
                }
            }
        }
    }
}
