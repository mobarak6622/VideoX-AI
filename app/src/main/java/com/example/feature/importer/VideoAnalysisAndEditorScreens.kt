package com.example.feature.importer

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.common.BilingualStrings
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.PreviewQuality
import com.example.core.common.VideoMetadata
import com.example.feature.StudioScreen
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import kotlin.math.roundToInt

@Composable
fun VideoAnalysisScreen(
    strings: BilingualStrings,
    metadata: VideoMetadata?,
    onOpenEditor: () -> Unit,
    onOpenUpscaler: () -> Unit,
    onOpenExport: () -> Unit,
    onPickAnotherVideo: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("video_analysis_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(strings.videoAnalysisTitle, style = MaterialTheme.typography.headlineMedium)
        }

        if (metadata == null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("No video currently loaded.", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onPickAnotherVideo) {
                            Text("Import Video")
                        }
                    }
                }
            }
            return@LazyColumn
        }

        if (!metadata.isSupported) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CrimsonError.copy(alpha = 0.15f)),
                    modifier = Modifier.border(1.dp, CrimsonError, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CrimsonError)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Unsupported Video Stream or Format",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrimsonError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = metadata.unsupportedReason ?: "This file cannot be decoded by local MediaCodec.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onPickAnotherVideo) {
                            Text("Select Supported Video (MP4 / MOV / MKV / WEBM)")
                        }
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(metadata.fileName, style = MaterialTheme.typography.titleLarge)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldSuccess.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = metadata.resolutionBadge,
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    MetadataRow("Dimensions", "${metadata.width} × ${metadata.height} px (${metadata.aspectRatioLabel})")
                    MetadataRow("Frame Rate", String.format("%.2f FPS (~%d frames)", metadata.frameRate, metadata.estimatedTotalFrames))
                    MetadataRow("Duration", formatDurationMs(metadata.durationMs))
                    MetadataRow("File Size", formatFileSize(metadata.fileSizeBytes))
                    MetadataRow("Video Codec", metadata.videoCodec)
                    MetadataRow("Audio Codec", metadata.audioCodec)
                    MetadataRow("Bitrate", if (metadata.bitrateBps > 0) "${metadata.bitrateBps / 1000} kbps" else "Variable Bitrate")
                    MetadataRow("Rotation Metadata", "${metadata.rotationDegrees}°")
                    MetadataRow("HDR Transfer", if (metadata.isHdr) "HDR10 / HLG Detected" else "SDR Standard Dynamic Range")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenEditor,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("btn_analysis_to_editor"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(strings.openInEditor)
                }
                Button(
                    onClick = onOpenUpscaler,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("btn_analysis_to_upscaler"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("4K AI Upscaler")
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_analysis_to_export"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Configure & Export MP4 Directly")
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoEditorScreen(
    strings: BilingualStrings,
    metadata: VideoMetadata?,
    pipeline: EnhancementPipelineState,
    originalFrame: Bitmap?,
    enhancedFrame: Bitmap?,
    previewTimestampMs: Long,
    canUndo: Boolean,
    canRedo: Boolean,
    onSeekTimestamp: (Long) -> Unit,
    onUpdatePreviewQuality: (PreviewQuality) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetAll: () -> Unit,
    onNavigateTool: (StudioScreen) -> Unit
) {
    var useLiveMedia3Player by remember { mutableStateOf(false) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var showOriginalOnly by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("video_editor_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (metadata == null) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Import or generate a video from the Home screen to open the Studio Editor.")
                    }
                }
            }
            return@LazyColumn
        }

        // Top Mode Bar: Frame Before/After Split vs Media3 Video Player + Undo/Redo/Reset
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useLiveMedia3Player,
                        onClick = { useLiveMedia3Player = false },
                        label = { Text("Before/After AI Frame") },
                        leadingIcon = { Icon(Icons.Default.Compare, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("chip_frame_compare_mode")
                    )
                    FilterChip(
                        selected = useLiveMedia3Player,
                        onClick = { useLiveMedia3Player = true },
                        label = { Text("Media3 Player") },
                        leadingIcon = { Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("chip_media3_player_mode")
                    )
                }

                Row {
                    IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.testTag("btn_editor_undo")) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.testTag("btn_editor_redo")) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = onResetAll, modifier = Modifier.testTag("btn_editor_reset")) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset All")
                    }
                }
            }
        }

        // Video Viewport (Interactive Split Frame Canvas OR Native Media3 ExoPlayer)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                if (useLiveMedia3Player) {
                    Media3VideoPlayerView(uriString = metadata.uriString)
                } else {
                    BeforeAfterFrameCanvas(
                        originalFrame = originalFrame,
                        enhancedFrame = if (showOriginalOnly) originalFrame else enhancedFrame,
                        splitFraction = splitFraction,
                        onSplitChanged = { splitFraction = it },
                        beforeLabel = strings.beforeLabel,
                        afterLabel = strings.afterLabel
                    )
                }
            }
        }

        // Frame Navigation & Timeline Scrubber
        if (!useLiveMedia3Player) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Timestamp: ${formatDurationMs(previewTimestampMs)} / ${formatDurationMs(metadata.durationMs)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            FilterChip(
                                selected = showOriginalOnly,
                                onClick = { showOriginalOnly = !showOriginalOnly },
                                label = { Text(if (showOriginalOnly) "Showing Original" else "Split Compare Active") }
                            )
                        }

                        Slider(
                            value = previewTimestampMs.toFloat(),
                            onValueChange = { onSeekTimestamp(it.toLong()) },
                            valueRange = 0f..metadata.durationMs.coerceAtLeast(100L).toFloat(),
                            modifier = Modifier.testTag("editor_timeline_slider")
                        )

                        val frameStepMs = (1000f / metadata.frameRate.coerceAtLeast(1f)).toLong().coerceAtLeast(16L)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { onSeekTimestamp((previewTimestampMs - frameStepMs).coerceAtLeast(0L)) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous Frame", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("-1 Frame")
                            }

                            Text(
                                text = "Drag preview to compare Before/After",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            OutlinedButton(
                                onClick = { onSeekTimestamp((previewTimestampMs + frameStepMs).coerceAtMost(metadata.durationMs)) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("+1 Frame")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.SkipNext, contentDescription = "Next Frame", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Preview Quality Selector (Never reduces final export quality)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Preview Decode Resolution (Does not reduce final export quality)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PreviewQuality.entries.forEach { q ->
                            FilterChip(
                                selected = pipeline.previewQuality == q,
                                onClick = { onUpdatePreviewQuality(q) },
                                label = { Text(q.label) }
                            )
                        }
                    }
                }
            }
        }

        // Processing Modules Strip
        item {
            Text("Enhancement Pipeline Modules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onNavigateTool(StudioScreen.AI_UPSCALER) },
                    label = { Text("Upscale: ${pipeline.upscale.targetResolution.shortBadge} (${pipeline.upscale.mode.name.take(6)})") }
                )
                AssistChip(
                    onClick = { onNavigateTool(StudioScreen.AUTO_COLOR) },
                    label = { Text("Color: ${if (pipeline.color.autoColor) "Auto" else pipeline.color.preset.displayName}") }
                )
                AssistChip(
                    onClick = { onNavigateTool(StudioScreen.FPS_CONVERTER) },
                    label = { Text("FPS: ${if (pipeline.fps.targetFps == 0) "Orig" else "${pipeline.fps.targetFps}fps"}") }
                )
                AssistChip(
                    onClick = { onNavigateTool(StudioScreen.STABILIZER) },
                    label = { Text("Stabilize: ${pipeline.stabilize.strength.label}") }
                )
                AssistChip(
                    onClick = { onNavigateTool(StudioScreen.DENOISER) },
                    label = { Text("Denoise: ${pipeline.denoise.strength.label}") }
                )
                AssistChip(
                    onClick = { onNavigateTool(StudioScreen.SHARPEN_DETAIL) },
                    label = { Text("Sharpen: ${(pipeline.sharpen.strength * 100).roundToInt()}%") }
                )
            }
        }

        item {
            Button(
                onClick = { onNavigateTool(StudioScreen.EXPORT_SETTINGS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("btn_editor_proceed_export"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
            ) {
                Text(strings.startExport, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun BeforeAfterFrameCanvas(
    originalFrame: Bitmap?,
    enhancedFrame: Bitmap?,
    splitFraction: Float,
    onSplitChanged: (Float) -> Unit,
    beforeLabel: String,
    afterLabel: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val newFrac = (change.position.x / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                    onSplitChanged(newFrac)
                }
            }
            .testTag("before_after_canvas")
    ) {
        if (originalFrame != null && enhancedFrame != null) {
            val origImg = remember(originalFrame) { originalFrame.asImageBitmap() }
            val enhImg = remember(enhancedFrame) { enhancedFrame.asImageBitmap() }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width.toInt()
                val canvasH = size.height.toInt()
                val splitX = size.width * splitFraction

                // Draw Enhanced on full canvas
                drawImage(
                    image = enhImg,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(canvasW, canvasH)
                )

                // Clip left side to Original
                clipRect(left = 0f, top = 0f, right = splitX, bottom = size.height) {
                    drawImage(
                        image = origImg,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(canvasW, canvasH)
                    )
                }

                // Draw vertical split divider line
                drawLine(
                    color = CyberBlue,
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, size.height),
                    strokeWidth = 4f
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Decoding actual video frame...", color = Color.White)
            }
        }

        // Before / After Corner Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                Text(
                    text = beforeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ElectricPurple.copy(alpha = 0.85f)
            ) {
                Text(
                    text = afterLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun Media3VideoPlayerView(uriString: String) {
    val context = LocalContext.current
    val exoPlayer = remember(uriString) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(uriString)))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        update = { view ->
            if (view.player != exoPlayer) {
                view.player = exoPlayer
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
    )
}

fun formatDurationMs(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val ms = (durationMs % 1000L) / 10L
    return String.format("%02d:%02d.%02d", min, sec, ms)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format("%.2f MB", mb)
    } else {
        String.format("%d KB", (bytes / 1024L).coerceAtLeast(1L))
    }
}
