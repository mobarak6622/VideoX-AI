package com.example.feature.importer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.VideoMetadata
import com.example.core.gpu.HardwareAccelerationManager
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class PreviewComparisonMode(val label: String) {
    SPLIT_SLIDER("Split Wipe"),
    DUAL_SYNC("Side-by-Side"),
    QUICK_FLIP("A / B Flip"),
    ENHANCED_ONLY("Pro Output")
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPreviewScreen(
    metadata: VideoMetadata?,
    processedVideoUriString: String?,
    pipeline: EnhancementPipelineState,
    onNavigateToEditor: () -> Unit,
    onNavigateToExport: () -> Unit,
    onQueueBackgroundTask: () -> Unit
) {
    val context = LocalContext.current
    val originalUri = metadata?.uriString ?: ""
    val hasProcessed = !processedVideoUriString.isNullOrBlank()
    val hwStatus = remember { HardwareAccelerationManager.inspectHardwareAcceleration(context) }

    var comparisonMode by remember {
        mutableStateOf(PreviewComparisonMode.SPLIT_SLIDER)
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(metadata?.durationMs ?: 1000L) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var showQuickOriginal by remember { mutableStateOf(false) }

    // Initialize Primary Media3 ExoPlayer for Original video (Hardware VPU Accelerated)
    val originalPlayer = remember(originalUri) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            if (originalUri.isNotBlank()) {
                setMediaItem(MediaItem.fromUri(Uri.parse(originalUri)))
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
                prepare()
            }
        }
    }

    // Initialize Secondary Media3 ExoPlayer for Processed video (or GPU Live Shader twin player)
    val targetEnhancedUri = if (hasProcessed) processedVideoUriString!! else originalUri
    val processedPlayer = remember(targetEnhancedUri) {
        if (targetEnhancedUri.isNotBlank()) {
            val renderersFactory = DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
            ExoPlayer.Builder(context, renderersFactory).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(targetEnhancedUri)))
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 0f // Secondary player stays muted so audio never echoes
                playWhenReady = true
                prepare()
            }
        } else {
            null
        }
    }

    // Synchronize play state, speed, loop, and position tracking
    DisposableEffect(originalPlayer, processedPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalDurationMs = originalPlayer.duration.coerceAtLeast(100L)
                }
            }
        }
        originalPlayer.addListener(listener)

        onDispose {
            originalPlayer.removeListener(listener)
            originalPlayer.release()
            processedPlayer?.release()
        }
    }

    // Playhead polling & tight drift correction loop so both hardware players stay locked
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = originalPlayer.currentPosition
            processedPlayer?.let { secondary ->
                val drift = kotlin.math.abs(secondary.currentPosition - currentPositionMs)
                if (drift > 90L) {
                    secondary.seekTo(currentPositionMs)
                }
            }
            delay(150)
        }
    }

    fun syncSeek(targetMs: Long) {
        currentPositionMs = targetMs
        originalPlayer.seekTo(targetMs)
        processedPlayer?.seekTo(targetMs)
    }

    fun togglePlayPause() {
        if (originalPlayer.isPlaying) {
            originalPlayer.pause()
            processedPlayer?.pause()
            isPlaying = false
        } else {
            originalPlayer.play()
            processedPlayer?.play()
            isPlaying = true
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        val vol = if (isMuted) 0f else 1f
        originalPlayer.volume = vol
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        originalPlayer.setPlaybackSpeed(speed)
        processedPlayer?.setPlaybackSpeed(speed)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("video_preview_screen")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // CapCut Pro Header & Hardware Acceleration Status Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ElectricPurple.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ElectricPurple
                            ) {
                                Text(
                                    "PRO CINEMA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "GPU / NPU Accelerated Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldSuccess.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = if (hwStatus.npuNnapiAvailable) "NPU + GPU ACTIVE" else "GPU + SIMD ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = if (hasProcessed) {
                            "Comparing Raw Input vs Exported CapCut Pro AI Video (${hwStatus.primaryHardwareDecoderName})"
                        } else {
                            "Zero-Stutter 60fps Live GPU Shader (${hwStatus.activeGpuPipelineName}) • ${hwStatus.activeAiDelegateName}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // View Mode Selector (Split Wipe, Dual Sync, A/B Flip, Pro Output)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreviewComparisonMode.entries.forEach { mode ->
                    FilterChip(
                        selected = comparisonMode == mode,
                        onClick = {
                            comparisonMode = mode
                            if (mode == PreviewComparisonMode.QUICK_FLIP) {
                                showQuickOriginal = !showQuickOriginal
                            } else {
                                showQuickOriginal = false
                            }
                        },
                        label = { Text(mode.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricPurple.copy(alpha = 0.3f),
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("chip_mode_${mode.name}")
                    )
                }
            }
        }

        // Main Viewport Container
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(1.dp, ElectricPurple.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            ) {
                when {
                    // Mode 1: Split Wipe Slider with Drag Bar
                    processedPlayer != null && comparisonMode == PreviewComparisonMode.SPLIT_SLIDER -> {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val totalWidthPx = constraints.maxWidth.toFloat()

                            // Base Player: Enhanced Video (Full width - either exported MP4 or real-time 60fps GPU shader)
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        player = processedPlayer
                                        useController = false
                                        HardwareAccelerationManager.applyLiveGpuShaderToView(
                                            view = this,
                                            pipeline = pipeline,
                                            enabled = !hasProcessed
                                        )
                                    }
                                },
                                update = { view ->
                                    if (view.player != processedPlayer) {
                                        view.player = processedPlayer
                                    }
                                    HardwareAccelerationManager.applyLiveGpuShaderToView(
                                        view = view,
                                        pipeline = pipeline,
                                        enabled = !hasProcessed
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Overlay Player: Original Raw Video (Clipped to split fraction)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = splitFraction)
                                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = originalPlayer
                                            useController = false
                                            HardwareAccelerationManager.applyLiveGpuShaderToView(
                                                view = this,
                                                pipeline = pipeline,
                                                enabled = false
                                            )
                                        }
                                    },
                                    update = { view ->
                                        if (view.player != originalPlayer) {
                                            view.player = originalPlayer
                                        }
                                        HardwareAccelerationManager.applyLiveGpuShaderToView(
                                            view = view,
                                            pipeline = pipeline,
                                            enabled = false
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Interactive Draggable Divider Line & Knob
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(x = (totalWidthPx * splitFraction - 18.dp.toPx()).roundToInt(), y = 0) }
                                    .fillMaxHeight()
                                    .width(36.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            splitFraction = (splitFraction + dragAmount.x / totalWidthPx).coerceIn(0.05f, 0.95f)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(2.5.dp)
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(ElectricPurple)
                                        .border(2.dp, Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Compare,
                                        contentDescription = "Slide to Compare",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Mode 2: Synchronized Dual View (Side-by-Side)
                    processedPlayer != null && comparisonMode == PreviewComparisonMode.DUAL_SYNC -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = originalPlayer
                                            useController = false
                                            HardwareAccelerationManager.applyLiveGpuShaderToView(
                                                view = this,
                                                pipeline = pipeline,
                                                enabled = false
                                            )
                                        }
                                    },
                                    update = { view ->
                                        HardwareAccelerationManager.applyLiveGpuShaderToView(
                                            view = view,
                                            pipeline = pipeline,
                                            enabled = false
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(8.dp)
                                ) {
                                    Text("BEFORE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Box(modifier = Modifier.width(2.dp).fillMaxHeight().background(ElectricPurple))
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = processedPlayer
                                            useController = false
                                            HardwareAccelerationManager.applyLiveGpuShaderToView(
                                                view = this,
                                                pipeline = pipeline,
                                                enabled = !hasProcessed
                                            )
                                        }
                                    },
                                    update = { view ->
                                        HardwareAccelerationManager.applyLiveGpuShaderToView(
                                            view = view,
                                            pipeline = pipeline,
                                            enabled = !hasProcessed
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ElectricPurple.copy(alpha = 0.85f),
                                    modifier = Modifier.padding(8.dp).align(Alignment.TopEnd)
                                ) {
                                    Text("PRO AI GPU", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }

                    // Mode 3: A/B Flip or Pro Output Single Player
                    else -> {
                        val activePlayer = if (hasProcessed && processedPlayer != null && !showQuickOriginal) {
                            processedPlayer
                        } else {
                            originalPlayer
                        }
                        val enableLiveShader = !hasProcessed && !showQuickOriginal

                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = activePlayer
                                    useController = false
                                    HardwareAccelerationManager.applyLiveGpuShaderToView(
                                        view = this,
                                        pipeline = pipeline,
                                        enabled = enableLiveShader
                                    )
                                }
                            },
                            update = { view ->
                                if (view.player != activePlayer) {
                                    view.player = activePlayer
                                }
                                HardwareAccelerationManager.applyLiveGpuShaderToView(
                                    view = view,
                                    pipeline = pipeline,
                                    enabled = enableLiveShader
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Top badges in Viewport (Before / After tag)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = if (showQuickOriginal) "ORIGINAL RAW" else "BEFORE / AFTER",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricPurple.copy(alpha = 0.9f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (hasProcessed) "EXPORTED PRO MP4" else "GPU LIVE SHADER • 60FPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // ExoPlayer Transport Controls
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Timecode and Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatDurationMs(currentPositionMs),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyberBlue
                        )
                        Text(
                            formatDurationMs(totalDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Slider(
                        value = (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            syncSeek((fraction * totalDurationMs).toLong())
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricPurple,
                            activeTrackColor = ElectricPurple,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("preview_seek_slider")
                    )

                    // Buttons Row: Rewind, Play/Pause, Fast-Forward, Speed, Mute, Loop
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { syncSeek((currentPositionMs - 3000L).coerceAtLeast(0L)) }) {
                                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 3s")
                            }

                            IconButton(
                                onClick = { togglePlayPause() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(ElectricPurple, CircleShape)
                                    .testTag("btn_preview_play_pause")
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White
                                )
                            }

                            IconButton(onClick = { syncSeek((currentPositionMs + 3000L).coerceAtMost(totalDurationMs)) }) {
                                Icon(Icons.Default.FastForward, contentDescription = "Forward 3s")
                            }
                        }

                        // Playback Speed Toggle (1x -> 1.5x -> 2x -> 0.5x)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                val nextSpeed = when (playbackSpeed) {
                                    1.0f -> 1.5f
                                    1.5f -> 2.0f
                                    2.0f -> 0.5f
                                    else -> 1.0f
                                }
                                setSpeed(nextSpeed)
                            }
                        ) {
                            Text(
                                "${playbackSpeed}x",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }

                        IconButton(onClick = { toggleMute() }) {
                            Icon(
                                if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                contentDescription = "Mute Toggle",
                                tint = if (isMuted) Color.Gray else CyberBlue
                            )
                        }
                    }
                }
            }
        }

        // Active Enhancement Badges Card (CapCut Pro Telemetry)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("CapCut Pro Enhancement Parameters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElectricPurple.copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Resolution", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    pipeline.upscale.targetResolution.shortBadge,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricPurple
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberBlue.copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("DSLR Bokeh", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    if (pipeline.bokeh.enabled) pipeline.bokeh.aperture else "Disabled",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberBlue
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldSuccess.copy(alpha = 0.2f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("AI Grade", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(
                                    pipeline.color.preset.displayName.substringBefore(" "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSuccess
                                )
                            }
                        }
                    }
                }
            }
        }

        // Primary Action Buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToEditor,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tune Studio")
                }

                Button(
                    onClick = onNavigateToExport,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                    modifier = Modifier.weight(1.3f).height(50.dp).testTag("btn_preview_export")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export MP4")
                }
            }
        }

        // Background Queue Button
        item {
            OutlinedButton(
                onClick = onQueueBackgroundTask,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("btn_queue_enhancement")
            ) {
                Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add to Background AI Processing Queue")
            }
        }
    }
}
