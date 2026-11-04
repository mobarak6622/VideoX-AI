package com.example.feature.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.timeline.TimelineClip
import com.example.core.timeline.TimelineProjectState
import com.example.core.timeline.TimelineTrack
import com.example.core.timeline.TimelineTrackType
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess
import kotlin.math.roundToInt

@Composable
fun MultiTrackTimelineView(
    state: TimelineProjectState,
    onSeekPlayhead: (Long) -> Unit,
    onSelectClip: (trackId: String, clipId: String) -> Unit,
    onSplitClip: () -> Unit,
    onDeleteClip: () -> Unit,
    onDuplicateClip: () -> Unit,
    onTrimClipStart: (clipId: String, newStartMs: Long) -> Unit,
    onTrimClipEnd: (clipId: String, newEndMs: Long) -> Unit,
    onToggleLockTrack: (trackId: String) -> Unit,
    onToggleMuteTrack: (trackId: String) -> Unit,
    onToggleVisibilityTrack: (trackId: String) -> Unit,
    onZoomChange: (Float) -> Unit,
    onAddTrackLayer: (TimelineTrackType) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val pxPerSecond = (120f * state.zoomLevel).coerceIn(40f, 400f)
    val totalSeconds = (state.totalDurationMs / 1000f).coerceAtLeast(10f)
    val timelineWidthDp = (totalSeconds * pxPerSecond).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF10121A))
            .border(1.dp, Color(0xFF222634))
    ) {
        // Quick Action Bar above Timeline (Split, Delete, Duplicate, Zoom, Add Layer)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161924))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Split at Playhead button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ElectricPurple.copy(alpha = 0.25f),
                    modifier = Modifier.clickable { onSplitClip() }.testTag("btn_timeline_split")
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCut, contentDescription = "Split", tint = ElectricPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Split", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ElectricPurple)
                    }
                }

                // Delete Clip button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.clickable { onDeleteClip() }.testTag("btn_timeline_delete")
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp, color = Color.White)
                    }
                }

                // Duplicate Clip button
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.clickable { onDuplicateClip() }.testTag("btn_timeline_duplicate")
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Duplicate", fontSize = 12.sp, color = Color.White)
                    }
                }
            }

            // Zoom In / Out Controls & Playhead Timecode
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = formatTimelineTimecode(state.playheadMs),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberBlue,
                    modifier = Modifier.padding(end = 6.dp)
                )

                IconButton(
                    onClick = { onZoomChange((state.zoomLevel - 0.2f).coerceAtLeast(0.6f)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }

                Text("${(state.zoomLevel * 100).toInt()}%", fontSize = 11.sp, color = Color.Gray)

                IconButton(
                    onClick = { onZoomChange((state.zoomLevel + 0.2f).coerceAtMost(3.0f)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Timeline Ruler and Scrollable Lanes Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Fixed Left Track Headers Column (V1, V2, TXT, BGM, SFX, FX)
                Column(
                    modifier = Modifier
                        .width(110.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF141722))
                        .border(1.dp, Color(0xFF222634))
                ) {
                    // Header corner (Ruler spacer)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .background(Color(0xFF191D2B))
                            .border(1.dp, Color(0xFF222634)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            "  LAYERS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray
                        )
                    }

                    // Track headers list
                    state.tracks.forEach { track ->
                        TrackHeaderItem(
                            track = track,
                            onToggleLock = { onToggleLockTrack(track.id) },
                            onToggleMute = { onToggleMuteTrack(track.id) },
                            onToggleVisibility = { onToggleVisibilityTrack(track.id) }
                        )
                    }
                }

                // Scrollable Right Area (Ruler + Clip Lanes + Playhead)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(scrollState)
                ) {
                    Column(modifier = Modifier.width(timelineWidthDp)) {
                        // 1. Time Ruler Canvas
                        TimelineRuler(
                            totalDurationMs = state.totalDurationMs,
                            pxPerSecond = pxPerSecond,
                            onSeek = onSeekPlayhead,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                        )

                        // 2. Track Clip Lanes
                        state.tracks.forEach { track ->
                            TrackLaneRow(
                                track = track,
                                activeClipId = state.activeClipId,
                                pxPerSecond = pxPerSecond,
                                onSelectClip = { clipId -> onSelectClip(track.id, clipId) },
                                onTrimStart = { clipId, newStart -> onTrimClipStart(clipId, newStart) },
                                onTrimEnd = { clipId, newEnd -> onTrimClipEnd(clipId, newEnd) }
                            )
                        }
                    }

                    // 3. Vertical Playhead Line with top Needle indicator
                    val playheadOffsetDp = ((state.playheadMs / 1000f) * pxPerSecond).dp
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(x = playheadOffsetDp.roundToPx() - 8.dp.roundToPx(), y = 0) }
                            .fillMaxHeight()
                            .width(16.dp)
                            .pointerInput(state.totalDurationMs, pxPerSecond) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaMs = ((dragAmount.x / pxPerSecond) * 1000f).toLong()
                                    onSeekPlayhead((state.playheadMs + deltaMs).coerceIn(0L, state.totalDurationMs))
                                }
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Playhead head knob
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(2.dp, ElectricPurple, CircleShape)
                        )
                        // Playhead needle line
                        Box(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackHeaderItem(
    track: TimelineTrack,
    onToggleLock: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVisibility: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(if (track.isLocked) Color(0xFF1E1724) else Color(0xFF161924))
            .border(0.5.dp, Color(0xFF232839))
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(track.type.colorHex).copy(alpha = 0.25f),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(
                    text = track.type.shortCode,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(track.type.colorHex),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Text(
                text = track.title.take(7),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.LightGray
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Lock",
                tint = if (track.isLocked) ElectricPurple else Color.DarkGray,
                modifier = Modifier
                    .size(15.dp)
                    .clickable { onToggleLock() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            if (track.type.isAudioTrack) {
                Icon(
                    imageVector = if (track.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = "Mute",
                    tint = if (track.isMuted) Color.Red else Color.Gray,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { onToggleMute() }
                )
            } else {
                Icon(
                    imageVector = if (track.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Visibility",
                    tint = if (track.isVisible) Color.Gray else Color.Red,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable { onToggleVisibility() }
                )
            }
        }
    }
}

@Composable
private fun TimelineRuler(
    totalDurationMs: Long,
    pxPerSecond: Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .background(Color(0xFF191D2B))
            .pointerInput(totalDurationMs, pxPerSecond) {
                detectTapGestures { offset ->
                    val seekSec = offset.x / pxPerSecond
                    onSeek((seekSec * 1000L).toLong().coerceIn(0L, totalDurationMs))
                }
            }
    ) {
        val totalSec = (totalDurationMs / 1000f).toInt() + 1
        for (sec in 0..totalSec) {
            val x = sec * pxPerSecond
            // Major tick mark
            drawLine(
                color = Color(0xFF6B7280),
                start = Offset(x, size.height * 0.45f),
                end = Offset(x, size.height),
                strokeWidth = 2f
            )
            // Minor sub-second ticks
            val quarterPx = pxPerSecond / 4f
            for (sub in 1..3) {
                drawLine(
                    color = Color(0xFF374151),
                    start = Offset(x + sub * quarterPx, size.height * 0.7f),
                    end = Offset(x + sub * quarterPx, size.height),
                    strokeWidth = 1f
                )
            }
        }
    }
}

@Composable
private fun TrackLaneRow(
    track: TimelineTrack,
    activeClipId: String?,
    pxPerSecond: Float,
    onSelectClip: (String) -> Unit,
    onTrimStart: (String, Long) -> Unit,
    onTrimEnd: (String, Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF0F1118))
            .border(0.5.dp, Color(0xFF1D2230))
    ) {
        // Clips inside this track
        track.clips.forEach { clip ->
            val startPx = (clip.startMs / 1000f) * pxPerSecond
            val widthPx = (clip.durationMs / 1000f) * pxPerSecond
            val isSelected = clip.id == activeClipId

            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(track.type.colorHex).copy(alpha = 0.85f)),
                modifier = Modifier
                    .offset { IntOffset(startPx.roundToInt(), 4.dp.roundToPx()) }
                    .width(widthPx.dp.coerceAtLeast(24.dp))
                    .height(36.dp)
                    .clickable { onSelectClip(clip.id) }
                    .border(
                        width = if (isSelected) 2.dp else 0.5.dp,
                        color = if (isSelected) Color.White else Color(track.type.colorHex),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = clip.textContent?.take(18) ?: clip.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${(clip.durationMs / 1000f)}s",
                        fontSize = 9.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

fun formatTimelineTimecode(timeMs: Long): String {
    val totalSec = timeMs / 1000L
    val ms = timeMs % 1000L
    val frames = ((ms / 1000f) * 30f).toInt()
    val sec = totalSec % 60L
    val min = (totalSec / 60L) % 60L
    val hrs = totalSec / 3600L
    return String.format("%02d:%02d:%02d:%02d", hrs, min, sec, frames)
}
