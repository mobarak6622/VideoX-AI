package com.example.feature.upscaler

import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.ai.InstalledAiModelInfo
import com.example.core.ai.LocalAiModelTask
import com.example.core.common.ColorConfig
import com.example.core.common.ColorPreset
import com.example.core.common.DenoiseConfig
import com.example.core.common.FilterStrength
import com.example.core.common.FpsConfig
import com.example.core.common.FpsConversionMode
import com.example.core.common.SharpenConfig
import com.example.core.common.StabilizeConfig
import com.example.core.common.TargetResolution
import com.example.core.common.UpscaleConfig
import com.example.core.common.UpscaleMode
import com.example.feature.importer.BeforeAfterFrameCanvas
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import kotlin.math.roundToInt

@Composable
fun AiUpscalerScreen(
    config: UpscaleConfig,
    installedModels: List<InstalledAiModelInfo>,
    originalFrame: Bitmap?,
    enhancedFrame: Bitmap?,
    onUpdate: (UpscaleConfig) -> Unit,
    onProceedToExport: () -> Unit
) {
    val srModel = installedModels.firstOrNull { it.task == LocalAiModelTask.SUPER_RESOLUTION }
    val isSrModelAvailable = srModel?.isAvailable == true
    var split by remember { mutableFloatStateOf(0.5f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_upscaler_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Local AI Super Resolution & 4K Upscaler", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Select target resolution and processing engine. Ordinary resizing is never labeled as AI Super Resolution.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (originalFrame != null && enhancedFrame != null) {
            item {
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    BeforeAfterFrameCanvas(
                        originalFrame = originalFrame,
                        enhancedFrame = enhancedFrame,
                        splitFraction = split,
                        onSplitChanged = { split = it },
                        beforeLabel = "INPUT",
                        afterLabel = config.mode.title.take(14)
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Target Output Resolution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TargetResolution.entries.forEach { res ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = config.targetResolution == res,
                                onClick = { onUpdate(config.copy(targetResolution = res)) },
                                modifier = Modifier.testTag("radio_res_${res.name}")
                            )
                            Text(res.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Upscaling Engine Mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    UpscaleMode.entries.forEach { mode ->
                        val enabled = mode == UpscaleMode.STANDARD_RESIZE || isSrModelAvailable
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = config.mode == mode,
                                enabled = enabled,
                                onClick = { onUpdate(config.copy(mode = mode)) },
                                modifier = Modifier.testTag("radio_upscale_mode_${mode.name}")
                            )
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(mode.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (enabled) mode.subtitle else "Disabled: Local ONNX Super Resolution model is missing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else AmberWarning
                                )
                            }
                        }
                    }

                    Text(
                        text = "Honest Transparency Notice: Scaling 720p to 4K increases pixel density and reconstructs edge sharpness via local ONNX convolutions, but cannot invent optical detail that was never captured by the camera sensor.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tiled Inference Memory Control", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Tile Size: ${config.tileSize}×${config.tileSize} px • Seam Feather Overlap: ${config.tileOverlap} px",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberBlue
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(128, 192, 256).forEach { tile ->
                            FilterChip(
                                selected = config.tileSize == tile,
                                onClick = { onUpdate(config.copy(tileSize = tile)) },
                                label = { Text("${tile}px Tile") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onProceedToExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_upscaler_to_export"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Apply & Configure Export")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoColorScreen(
    config: ColorConfig,
    originalFrame: Bitmap?,
    enhancedFrame: Bitmap?,
    onApplyOneTapAuto: () -> Unit,
    onUpdate: (ColorConfig) -> Unit
) {
    var split by remember { mutableFloatStateOf(0.5f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("auto_color_screen"),
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
                    Text("Intelligent Auto Color Engine", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "14-axis local grading with skin-tone protection & temporal flicker smoothing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onApplyOneTapAuto,
                    modifier = Modifier.testTag("btn_one_tap_auto_color"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("AUTO COLOR")
                }
            }
        }

        if (originalFrame != null && enhancedFrame != null) {
            item {
                Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(190.dp)) {
                    BeforeAfterFrameCanvas(
                        originalFrame = originalFrame,
                        enhancedFrame = enhancedFrame,
                        splitFraction = split,
                        onSplitChanged = { split = it },
                        beforeLabel = "ORIGINAL",
                        afterLabel = "GRADED (${config.intensityPercent.roundToInt()}%)"
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Color Engine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = config.enabled || config.autoColor,
                            onCheckedChange = { onUpdate(config.copy(enabled = it, autoColor = if (!it) false else config.autoColor)) }
                        )
                    }

                    Text("Master Intensity: ${config.intensityPercent.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = config.intensityPercent,
                        onValueChange = { onUpdate(config.copy(enabled = true, intensityPercent = it)) },
                        valueRange = 0f..100f
                    )

                    Text("Studio Color Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ColorPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = config.preset == preset,
                                onClick = { onUpdate(config.copy(enabled = true, preset = preset)) },
                                label = { Text(preset.displayName) },
                                modifier = Modifier.testTag("chip_preset_${preset.name}")
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("14-Parameter Fine Calibration", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    ColorSliderRow("Exposure", config.exposure, -1f..1f) { onUpdate(config.copy(enabled = true, exposure = it)) }
                    ColorSliderRow("Brightness", config.brightness, -0.5f..0.5f) { onUpdate(config.copy(enabled = true, brightness = it)) }
                    ColorSliderRow("Contrast", config.contrast, -0.5f..0.5f) { onUpdate(config.copy(enabled = true, contrast = it)) }
                    ColorSliderRow("Saturation", config.saturation, -1f..1f) { onUpdate(config.copy(enabled = true, saturation = it)) }
                    ColorSliderRow("Vibrance (Skin-Safe)", config.vibrance, -1f..1f) { onUpdate(config.copy(enabled = true, vibrance = it)) }
                    ColorSliderRow("White Balance", config.whiteBalance, -1f..1f) { onUpdate(config.copy(enabled = true, whiteBalance = it)) }
                    ColorSliderRow("Temperature", config.temperature, -1f..1f) { onUpdate(config.copy(enabled = true, temperature = it)) }
                    ColorSliderRow("Tint", config.tint, -1f..1f) { onUpdate(config.copy(enabled = true, tint = it)) }
                    ColorSliderRow("Highlights", config.highlights, -1f..1f) { onUpdate(config.copy(enabled = true, highlights = it)) }
                    ColorSliderRow("Shadows", config.shadows, -1f..1f) { onUpdate(config.copy(enabled = true, shadows = it)) }
                    ColorSliderRow("Gamma", config.gamma, 0.5f..2.0f) { onUpdate(config.copy(enabled = true, gamma = it)) }
                    ColorSliderRow("Black Level", config.blackLevel, -0.2f..0.2f) { onUpdate(config.copy(enabled = true, blackLevel = it)) }
                    ColorSliderRow("White Level", config.whiteLevel, -0.2f..0.2f) { onUpdate(config.copy(enabled = true, whiteLevel = it)) }
                }
            }
        }
    }
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall)
            Text(String.format("%.2f", value), style = MaterialTheme.typography.labelMedium, color = ElectricPurple)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FpsConverterScreen(
    config: FpsConfig,
    onUpdate: (FpsConfig) -> Unit
) {
    val supportedFpsList = listOf(0, 24, 25, 30, 50, 60, 90, 120)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("fps_converter_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("FPS Conversion & Frame Interpolation", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Separates Standard Timestamp Resampling from AI Optical Flow + RIFE Frame Interpolation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Target Frame Rate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        supportedFpsList.forEach { fps ->
                            FilterChip(
                                selected = config.targetFps == fps,
                                onClick = { onUpdate(config.copy(targetFps = fps)) },
                                label = { Text(if (fps == 0) "Original FPS" else "$fps FPS") },
                                modifier = Modifier.testTag("chip_fps_$fps")
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Motion Processing System", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FpsConversionMode.entries.forEach { mode ->
                        Row(verticalAlignment = Alignment.Top) {
                            RadioButton(
                                selected = config.mode == mode,
                                onClick = { onUpdate(config.copy(mode = mode)) },
                                modifier = Modifier.testTag("radio_fps_mode_${mode.name}")
                            )
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(mode.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scene-Cut Detection", style = MaterialTheme.typography.titleSmall)
                            Text("Prevents morphing artifacts across hard camera cuts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.sceneCutDetection,
                            onCheckedChange = { onUpdate(config.copy(sceneCutDetection = it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StabilizerScreen(
    config: StabilizeConfig,
    onUpdate: (StabilizeConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("stabilizer_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Video Stabilization Engine", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Estimates inter-frame motion vectors and applies trajectory smoothing with transparent crop telemetry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Stabilization Intensity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(FilterStrength.OFF, FilterStrength.LOW, FilterStrength.MEDIUM, FilterStrength.HIGH).forEach { st ->
                            FilterChip(
                                selected = config.strength == st,
                                onClick = { onUpdate(config.copy(strength = st)) },
                                label = { Text(st.label) },
                                modifier = Modifier.testTag("chip_stabilize_${st.name}")
                            )
                        }
                    }

                    Text(
                        text = "Estimated Framing Crop Loss: ${String.format("%.1f%%", config.estimatedFramingLossPercent)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyberBlue
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rolling Shutter Correction", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = config.rollingShutterCorrection,
                            onCheckedChange = { onUpdate(config.copy(rollingShutterCorrection = it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DenoiserScreen(
    config: DenoiseConfig,
    onUpdate: (DenoiseConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("denoiser_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("AI Video Denoising Engine", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Combines spatial bilateral filtering, temporal motion-gated noise reduction, and ONNX CNN deblocking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Denoising Preset Level", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterStrength.entries.forEach { level ->
                            FilterChip(
                                selected = config.strength == level,
                                onClick = { onUpdate(config.copy(strength = level)) },
                                label = { Text(level.label) },
                                modifier = Modifier.testTag("chip_denoise_${level.name}")
                            )
                        }
                    }

                    ColorSliderRow("Spatial Noise Reduction", config.spatialStrength, 0f..1f) {
                        onUpdate(config.copy(strength = FilterStrength.CUSTOM, spatialStrength = it))
                    }
                    ColorSliderRow("Temporal Frame Smoothing", config.temporalStrength, 0f..1f) {
                        onUpdate(config.copy(strength = FilterStrength.CUSTOM, temporalStrength = it))
                    }

                    ToggleSettingRow("Preserve Natural Skin & Fine Edges", config.preserveSkinAndEdges) {
                        onUpdate(config.copy(preserveSkinAndEdges = it))
                    }
                    ToggleSettingRow("Macroblock & Compression Artifact Reduction", config.compressionArtifactReduction) {
                        onUpdate(config.copy(compressionArtifactReduction = it))
                    }
                    ToggleSettingRow("8-Bit Gradient Debanding Dither", config.debanding) {
                        onUpdate(config.copy(debanding = it))
                    }
                }
            }
        }
    }
}

@Composable
fun SharpenDetailScreen(
    config: SharpenConfig,
    onUpdate: (SharpenConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sharpen_detail_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Sharpening & Detail Recovery", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Halo-suppressed unsharp masking, micro-contrast clarity, and fine texture recovery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ColorSliderRow("Smart Sharpening", config.strength, 0f..1f) { onUpdate(config.copy(strength = it)) }
                    ColorSliderRow("Edge Enhancement", config.edgeEnhancement, 0f..1f) { onUpdate(config.copy(edgeEnhancement = it)) }
                    ColorSliderRow("Clarity (Mid-Tone Contrast)", config.clarity, 0f..1f) { onUpdate(config.copy(clarity = it)) }
                    ColorSliderRow("Fine Detail & Texture Recovery", config.fineDetailRecovery, 0f..1f) {
                        onUpdate(config.copy(fineDetailRecovery = it, textureRecovery = it))
                    }
                    ToggleSettingRow("Motion Deblurring Pass", config.deblurring) {
                        onUpdate(config.copy(deblurring = it))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
