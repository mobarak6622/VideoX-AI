package com.example.feature.editor

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.core.common.BilingualStrings
import com.example.core.common.ColorConfig
import com.example.core.common.EnhancementPipelineState
import com.example.core.common.VideoMetadata
import com.example.core.gpu.HardwareAccelerationManager
import com.example.core.timeline.CanvasAspectRatio
import com.example.core.timeline.TimelineClip
import com.example.core.timeline.TimelineEngine
import com.example.core.timeline.TimelineProjectState
import com.example.core.timeline.TimelineTrack
import com.example.core.timeline.TimelineTrackType
import com.example.feature.timeline.MultiTrackTimelineView
import com.example.feature.timeline.formatTimelineTimecode
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun EditXStudioScreen(
    strings: BilingualStrings,
    metadata: VideoMetadata?,
    pipeline: EnhancementPipelineState,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExportVideo: () -> Unit,
    onUpdateColorConfig: (ColorConfig) -> Unit,
    onOneTapAutoColor: () -> Unit,
    onOpenAiUpscaler: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uriString = metadata?.uriString ?: ""
    val durationMs = metadata?.durationMs ?: 10000L

    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var canvasRatio by remember { mutableStateOf(CanvasAspectRatio.RATIO_9_16) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showSafeGuides by remember { mutableStateOf(true) }
    var activeTool by remember { mutableStateOf<ActiveEditorTool?>(null) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var showSplitWipe by remember { mutableStateOf(false) }

    // Multi-track Timeline Project State
    var timelineState by remember(uriString) {
        val defaultTracks = TimelineEngine.createDefaultTracks().toMutableList()
        // If a real video was loaded, populate Main Video track with initial clip
        if (uriString.isNotBlank()) {
            val mainTrackIdx = defaultTracks.indexOfFirst { it.type == TimelineTrackType.MAIN_VIDEO }
            if (mainTrackIdx != -1) {
                val initialClip = TimelineClip(
                    trackType = TimelineTrackType.MAIN_VIDEO,
                    title = metadata?.fileName ?: "Main Video Clip",
                    sourceUri = uriString,
                    startMs = 0L,
                    endMs = durationMs
                )
                defaultTracks[mainTrackIdx] = defaultTracks[mainTrackIdx].copy(clips = listOf(initialClip))
            }
        }
        mutableStateOf(
            TimelineProjectState(
                projectName = metadata?.fileName?.substringBeforeLast(".") ?: "EditX Master Project",
                canvasRatio = CanvasAspectRatio.RATIO_9_16,
                tracks = defaultTracks,
                totalDurationMs = durationMs.coerceAtLeast(5000L),
                activeClipId = defaultTracks.flatMap { it.clips }.firstOrNull()?.id
            )
        )
    }

    // Media3 ExoPlayer with Hardware Acceleration
    val exoPlayer = remember(uriString) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            if (uriString.isNotBlank()) {
                setMediaItem(MediaItem.fromUri(android.net.Uri.parse(uriString)))
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = false
                prepare()
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Playhead polling loop
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPositionMs = exoPlayer.currentPosition
            timelineState = timelineState.copy(playheadMs = currentPositionMs)
            delay(100)
        }
    }

    fun seekToPosition(targetMs: Long) {
        currentPositionMs = targetMs
        timelineState = timelineState.copy(playheadMs = targetMs)
        exoPlayer.seekTo(targetMs)
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
            isPlaying = false
        } else {
            exoPlayer.play()
            isPlaying = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14))
            .testTag("editx_studio_screen")
    ) {
        // 1. Top CapCut Pro Studio Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141722))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_editor_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text(
                        text = timelineState.projectName.take(16),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text("Auto-Saved (Room SQLite)", fontSize = 10.sp, color = EmeraldSuccess)
                }
            }

            // Undo / Redo & Aspect Ratio Selector
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) Color.White else Color.DarkGray)
                }
                IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) Color.White else Color.DarkGray)
                }

                // Ratio chip toggle (9:16 -> 16:9 -> 1:1 -> 4:5)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.1f),
                    modifier = Modifier.clickable {
                        val nextRatio = when (canvasRatio) {
                            CanvasAspectRatio.RATIO_9_16 -> CanvasAspectRatio.RATIO_16_9
                            CanvasAspectRatio.RATIO_16_9 -> CanvasAspectRatio.RATIO_1_1
                            CanvasAspectRatio.RATIO_1_1 -> CanvasAspectRatio.RATIO_4_5
                            else -> CanvasAspectRatio.RATIO_9_16
                        }
                        canvasRatio = nextRatio
                        timelineState = timelineState.copy(canvasRatio = nextRatio)
                    }
                ) {
                    Text(
                        canvasRatio.label.substringBefore(" "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberBlue,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }

                // Export Button
                Button(
                    onClick = onExportVideo,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.height(36.dp).testTag("btn_editor_export")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 2. Video Viewport with Aspect Ratio Masking & Safe Area Guides
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val aspectModifier = when (canvasRatio) {
                CanvasAspectRatio.RATIO_9_16 -> Modifier.aspectRatio(9f / 16f)
                CanvasAspectRatio.RATIO_16_9 -> Modifier.aspectRatio(16f / 9f)
                CanvasAspectRatio.RATIO_1_1 -> Modifier.aspectRatio(1f / 1f)
                CanvasAspectRatio.RATIO_4_5 -> Modifier.aspectRatio(4f / 5f)
                else -> Modifier.aspectRatio(9f / 16f)
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(aspectModifier)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF07080C))
                    .border(1.dp, Color(0xFF222634))
            ) {
                // Real-time ExoPlayer Surface with Live GPU Shader filter
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            HardwareAccelerationManager.applyLiveGpuShaderToView(
                                view = this,
                                pipeline = pipeline,
                                enabled = true
                            )
                        }
                    },
                    update = { view ->
                        HardwareAccelerationManager.applyLiveGpuShaderToView(
                            view = view,
                            pipeline = pipeline,
                            enabled = true
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Safe Area Guides Overlay (Rule of Thirds + Social Media margins)
                if (showSafeGuides) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        // Rule of thirds lines
                        drawLine(Color(0x33FFFFFF), Offset(w / 3f, 0f), Offset(w / 3f, h), 1f)
                        drawLine(Color(0x33FFFFFF), Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), 1f)
                        drawLine(Color(0x33FFFFFF), Offset(0f, h / 3f), Offset(w, h / 3f), 1f)
                        drawLine(Color(0x33FFFFFF), Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), 1f)
                    }
                }

                // Viewport Badges (Ratio + 60fps GPU badge)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.6f)) {
                        Text(canvasRatio.label, fontSize = 9.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = EmeraldSuccess.copy(alpha = 0.85f)) {
                        Text("GPU 60FPS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }

        // 3. Mini Transport Bar (Rewind, Play/Pause, Forward, Timecode)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF12141D))
                .padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { seekToPosition((currentPositionMs - 2000L).coerceAtLeast(0L)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Rewind", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }

                IconButton(
                    onClick = { togglePlayPause() },
                    modifier = Modifier.size(36.dp).background(ElectricPurple, CircleShape).testTag("btn_studio_play_pause")
                ) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                IconButton(onClick = { seekToPosition((currentPositionMs + 2000L).coerceAtMost(durationMs)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.FastForward, contentDescription = "Forward", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                }
            }

            Text(
                text = "${formatTimelineTimecode(currentPositionMs)} / ${formatTimelineTimecode(durationMs)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            // Safe guide toggle
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (showSafeGuides) CyberBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                modifier = Modifier.clickable { showSafeGuides = !showSafeGuides }
            ) {
                Text(
                    "GUIDES",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (showSafeGuides) CyberBlue else Color.Gray,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }

        // 4. CapCut Pro Multi-Track Timeline (Main Video, Text, Audio, VFX layers)
        MultiTrackTimelineView(
            state = timelineState,
            onSeekPlayhead = { seekToPosition(it) },
            onSelectClip = { trackId, clipId ->
                timelineState = timelineState.copy(activeTrackId = trackId, activeClipId = clipId)
            },
            onSplitClip = {
                val activeClip = timelineState.tracks.flatMap { it.clips }.find { it.id == timelineState.activeClipId }
                if (activeClip != null) {
                    val updated = TimelineEngine.splitClipAt(
                        tracks = timelineState.tracks,
                        trackId = activeClip.trackType.id,
                        clipId = activeClip.id,
                        playheadMs = currentPositionMs
                    )
                    timelineState = timelineState.copy(tracks = updated)
                }
            },
            onDeleteClip = {
                val clipId = timelineState.activeClipId
                if (clipId != null) {
                    val updated = TimelineEngine.deleteClip(timelineState.tracks, clipId, ripple = true)
                    timelineState = timelineState.copy(tracks = updated, activeClipId = null)
                }
            },
            onDuplicateClip = {
                val clipId = timelineState.activeClipId
                if (clipId != null) {
                    val updated = TimelineEngine.duplicateClip(timelineState.tracks, clipId)
                    timelineState = timelineState.copy(tracks = updated)
                }
            },
            onTrimClipStart = { clipId, newStart ->
                val updated = TimelineEngine.trimClipStart(timelineState.tracks, clipId, newStart)
                timelineState = timelineState.copy(tracks = updated)
            },
            onTrimClipEnd = { clipId, newEnd ->
                val updated = TimelineEngine.trimClipEnd(timelineState.tracks, clipId, newEnd)
                timelineState = timelineState.copy(tracks = updated)
            },
            onToggleLockTrack = { trackId ->
                val updated = timelineState.tracks.map { if (it.id == trackId) it.copy(isLocked = !it.isLocked) else it }
                timelineState = timelineState.copy(tracks = updated)
            },
            onToggleMuteTrack = { trackId ->
                val updated = timelineState.tracks.map { if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it }
                timelineState = timelineState.copy(tracks = updated)
            },
            onToggleVisibilityTrack = { trackId ->
                val updated = timelineState.tracks.map { if (it.id == trackId) it.copy(isVisible = !it.isVisible) else it }
                timelineState = timelineState.copy(tracks = updated)
            },
            onZoomChange = { newZoom ->
                timelineState = timelineState.copy(zoomLevel = newZoom)
            },
            onAddTrackLayer = { trackType ->
                val newClip = TimelineClip(
                    trackType = trackType,
                    title = "New ${trackType.title} Clip",
                    startMs = currentPositionMs,
                    endMs = currentPositionMs + 4000L
                )
                val updated = timelineState.tracks.map {
                    if (it.type == trackType) it.copy(clips = it.clips + newClip) else it
                }
                timelineState = timelineState.copy(tracks = updated, activeClipId = newClip.id)
            }
        )

        // 5. Quick Tool Selector Dock (CapCut Pro Style Bottom Dock)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141722))
                .border(1.dp, Color(0xFF222634))
                .padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                StudioDockItem("Edit", Icons.Default.ContentCut, activeTool == ActiveEditorTool.BASIC_EDIT) {
                    activeTool = if (activeTool == ActiveEditorTool.BASIC_EDIT) null else ActiveEditorTool.BASIC_EDIT
                }
            }
            item {
                StudioDockItem("Speed", Icons.Default.Speed, activeTool == ActiveEditorTool.SPEED_STUDIO) {
                    activeTool = if (activeTool == ActiveEditorTool.SPEED_STUDIO) null else ActiveEditorTool.SPEED_STUDIO
                }
            }
            item {
                StudioDockItem("Color", Icons.Default.ColorLens, activeTool == ActiveEditorTool.COLOR_STUDIO) {
                    activeTool = if (activeTool == ActiveEditorTool.COLOR_STUDIO) null else ActiveEditorTool.COLOR_STUDIO
                }
            }
            item {
                StudioDockItem("Text / বাংলা", Icons.Default.TextFields, activeTool == ActiveEditorTool.TEXT_BENGALI) {
                    activeTool = if (activeTool == ActiveEditorTool.TEXT_BENGALI) null else ActiveEditorTool.TEXT_BENGALI
                }
            }
            item {
                StudioDockItem("Audio", Icons.Default.MusicNote, activeTool == ActiveEditorTool.AUDIO_STUDIO) {
                    activeTool = if (activeTool == ActiveEditorTool.AUDIO_STUDIO) null else ActiveEditorTool.AUDIO_STUDIO
                }
            }
            item {
                StudioDockItem("AI Studio", Icons.Default.AutoAwesome, false) {
                    onOpenAiUpscaler()
                }
            }
        }

        // 6. Sliding Tool Panel Sheet
        AnimatedVisibility(
            visible = activeTool != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181B26)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .border(1.dp, Color(0xFF2A2E40), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(activeTool?.title ?: "", fontWeight = FontWeight.Bold, color = Color.White)
                        IconButton(onClick = { activeTool = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    when (activeTool) {
                        ActiveEditorTool.SPEED_STUDIO -> SpeedControlStudioPanel(
                            currentSpeed = 1.0f,
                            onSpeedChange = { newSpeed ->
                                exoPlayer.setPlaybackSpeed(newSpeed)
                            },
                            onApplySpeedCurve = { curve ->
                                // Apply speed curve
                            }
                        )
                        ActiveEditorTool.COLOR_STUDIO -> ColorGradingStudioPanel(
                            config = pipeline.color,
                            onUpdateConfig = onUpdateColorConfig,
                            onOneTapAutoColor = onOneTapAutoColor
                        )
                        ActiveEditorTool.TEXT_BENGALI -> TextAndBengaliStudioPanel(
                            onAddTextClip = { text, font, isBengali ->
                                val textClip = TimelineClip(
                                    trackType = TimelineTrackType.TEXT,
                                    title = text.take(12),
                                    textContent = text,
                                    fontName = font,
                                    isBengaliText = isBengali,
                                    startMs = currentPositionMs,
                                    endMs = currentPositionMs + 3500L
                                )
                                val updated = timelineState.tracks.map {
                                    if (it.type == TimelineTrackType.TEXT) it.copy(clips = it.clips + textClip) else it
                                }
                                timelineState = timelineState.copy(tracks = updated, activeClipId = textClip.id)
                                activeTool = null
                            },
                            onGenerateAutoCaptions = {
                                val captionClip = TimelineClip(
                                    trackType = TimelineTrackType.SUBTITLE,
                                    title = "স্বয়ংক্রিয় সাবটাইটেল",
                                    textContent = "EditX AI স্বয়ংক্রিয় সাবটাইটেল প্রস্তুত",
                                    isBengaliText = true,
                                    startMs = 0L,
                                    endMs = durationMs
                                )
                                val updated = timelineState.tracks.map {
                                    if (it.type == TimelineTrackType.SUBTITLE) it.copy(clips = listOf(captionClip)) else it
                                }
                                timelineState = timelineState.copy(tracks = updated)
                                activeTool = null
                            }
                        )
                        ActiveEditorTool.AUDIO_STUDIO -> AudioStudioPanel(
                            onAddMusic = { musicName ->
                                val musicClip = TimelineClip(
                                    trackType = TimelineTrackType.BACKGROUND_MUSIC,
                                    title = musicName,
                                    startMs = 0L,
                                    endMs = durationMs
                                )
                                val updated = timelineState.tracks.map {
                                    if (it.type == TimelineTrackType.BACKGROUND_MUSIC) it.copy(clips = listOf(musicClip)) else it
                                }
                                timelineState = timelineState.copy(tracks = updated)
                                activeTool = null
                            },
                            onAddSfx = { sfxName ->
                                val sfxClip = TimelineClip(
                                    trackType = TimelineTrackType.SOUND_EFFECTS,
                                    title = sfxName,
                                    startMs = currentPositionMs,
                                    endMs = currentPositionMs + 1500L
                                )
                                val updated = timelineState.tracks.map {
                                    if (it.type == TimelineTrackType.SOUND_EFFECTS) it.copy(clips = it.clips + sfxClip) else it
                                }
                                timelineState = timelineState.copy(tracks = updated)
                                activeTool = null
                            },
                            onStartVoiceover = {
                                val voxClip = TimelineClip(
                                    trackType = TimelineTrackType.VOICEOVER,
                                    title = "Live Studio Voiceover",
                                    startMs = currentPositionMs,
                                    endMs = currentPositionMs + 5000L
                                )
                                val updated = timelineState.tracks.map {
                                    if (it.type == TimelineTrackType.VOICEOVER) it.copy(clips = it.clips + voxClip) else it
                                }
                                timelineState = timelineState.copy(tracks = updated)
                                activeTool = null
                            }
                        )
                        else -> {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text("Tool active in workspace.", color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioDockItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isSelected) ElectricPurple else Color(0xFF222636)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = if (isSelected) Color.White else Color.LightGray, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = if (isSelected) ElectricPurple else Color.LightGray)
    }
}
