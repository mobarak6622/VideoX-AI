package com.example.core.timeline

import java.util.UUID

/**
 * 12 Professional CapCut Pro Multi-Track Layer Types for EditX AI
 */
enum class TimelineTrackType(
    val id: String,
    val title: String,
    val shortCode: String,
    val isAudioTrack: Boolean,
    val colorHex: Long
) {
    MAIN_VIDEO("main_video", "Main Video", "V1", false, 0xFF7C4DFF),
    SECONDARY_VIDEO("secondary_video", "Video PIP / Layer 2", "V2", false, 0xFF651FFF),
    OVERLAY_VIDEO("overlay_video", "Overlay / B-Roll", "OVL", false, 0xFF536DFE),
    IMAGE("image_track", "Images / Photos", "IMG", false, 0xFF00B0FF),
    TEXT("text_track", "Text & Titles", "TXT", false, 0xFFFFAB00),
    SUBTITLE("subtitle_track", "Captions & Subtitles", "CC", false, 0xFFFFD600),
    STICKER("sticker_track", "Stickers & Emojis", "STK", false, 0xFFFF4081),
    BACKGROUND_MUSIC("bgm_track", "Background Music", "BGM", true, 0xFF00E676),
    VOICEOVER("voiceover_track", "Voiceover Recording", "VOX", true, 0xFF00BFA5),
    SOUND_EFFECTS("sfx_track", "Sound Effects", "SFX", true, 0xFF1DE9B6),
    VISUAL_EFFECTS("vfx_track", "Visual Effects (VFX)", "FX", false, 0xFFFF1744),
    ADJUSTMENT_LAYER("adjustment_layer", "Adjustment Layer", "ADJ", false, 0xFFD500F9)
}

/**
 * Canvas aspect ratios supported by EditX AI
 */
enum class CanvasAspectRatio(val label: String, val ratio: Float, val iconDescription: String) {
    RATIO_9_16("9:16 (TikTok/Reels)", 9f / 16f, "Vertical Video"),
    RATIO_16_9("16:9 (YouTube/Cinema)", 16f / 9f, "Horizontal Video"),
    RATIO_1_1("1:1 (Square/Instagram)", 1f / 1f, "Square Video"),
    RATIO_4_5("4:5 (Portrait Feed)", 4f / 5f, "Portrait Feed"),
    RATIO_3_4("3:4 (Classic Portrait)", 3f / 4f, "Classic Feed"),
    RATIO_CUSTOM("Custom Fit", 0f, "Original Fit")
}

/**
 * Keyframe for motion & parameter animation
 */
data class TimelineKeyframe(
    val timestampMs: Long,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val volume: Float = 1f
)

/**
 * Represents a single real media or effect clip placed in a track
 */
data class TimelineClip(
    val id: String = UUID.randomUUID().toString(),
    val trackType: TimelineTrackType,
    val title: String,
    val sourceUri: String = "",
    val startMs: Long,
    val endMs: Long,
    val trimInMs: Long = 0L,
    val trimOutMs: Long = 0L,
    val speed: Float = 1.0f,
    val volume: Float = 1.0f,
    val opacity: Float = 1.0f,
    val scale: Float = 1.0f,
    val rotation: Float = 0f,
    val posX: Float = 0f,
    val posY: Float = 0f,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val effectName: String? = null,
    val textContent: String? = null,
    val fontName: String = "Roboto",
    val textColorHex: Long = 0xFFFFFFFF,
    val backgroundColorHex: Long = 0x00000000,
    val isBengaliText: Boolean = false,
    val keyframes: List<TimelineKeyframe> = emptyList()
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(100L)
}

/**
 * Represents a layer track in the multi-layer timeline
 */
data class TimelineTrack(
    val id: String = UUID.randomUUID().toString(),
    val type: TimelineTrackType,
    val title: String = type.title,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val clips: List<TimelineClip> = emptyList()
)

/**
 * Represents an entire video editing project timeline
 */
data class TimelineProjectState(
    val projectId: String = UUID.randomUUID().toString(),
    val projectName: String = "Untitled EditX Project",
    val canvasRatio: CanvasAspectRatio = CanvasAspectRatio.RATIO_9_16,
    val tracks: List<TimelineTrack> = emptyList(),
    val activeClipId: String? = null,
    val activeTrackId: String? = null,
    val playheadMs: Long = 0L,
    val totalDurationMs: Long = 10000L,
    val zoomLevel: Float = 1.0f, // 0.5x to 4.0x zoom
    val magneticSnapping: Boolean = true,
    val safeAreaGuides: Boolean = true,
    val isFullscreenPreview: Boolean = false
)

object TimelineEngine {

    /**
     * Creates default standard 12 tracks for a new EditX project
     */
    fun createDefaultTracks(): List<TimelineTrack> {
        return TimelineTrackType.entries.map { type ->
            TimelineTrack(
                id = type.id,
                type = type,
                title = type.title,
                clips = emptyList()
            )
        }
    }

    /**
     * Splits a clip at the current playhead position into two non-destructive segments
     */
    fun splitClipAt(
        tracks: List<TimelineTrack>,
        trackId: String,
        clipId: String,
        playheadMs: Long
    ): List<TimelineTrack> {
        return tracks.map { track ->
            if (track.id != trackId && track.type.id != trackId) {
                track
            } else {
                val updatedClips = mutableListOf<TimelineClip>()
                for (clip in track.clips) {
                    if (clip.id == clipId && playheadMs > clip.startMs + 100L && playheadMs < clip.endMs - 100L) {
                        val firstDuration = playheadMs - clip.startMs
                        val firstTrimOut = clip.trimInMs + (firstDuration * clip.speed).toLong()

                        val clipA = clip.copy(
                            id = UUID.randomUUID().toString(),
                            title = "${clip.title} (Part 1)",
                            endMs = playheadMs,
                            trimOutMs = firstTrimOut
                        )

                        val clipB = clip.copy(
                            id = UUID.randomUUID().toString(),
                            title = "${clip.title} (Part 2)",
                            startMs = playheadMs,
                            trimInMs = firstTrimOut
                        )

                        updatedClips.add(clipA)
                        updatedClips.add(clipB)
                    } else {
                        updatedClips.add(clip)
                    }
                }
                track.copy(clips = updatedClips)
            }
        }
    }

    /**
     * Trims a clip's start boundary
     */
    fun trimClipStart(
        tracks: List<TimelineTrack>,
        clipId: String,
        newStartMs: Long
    ): List<TimelineTrack> {
        return tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId && newStartMs < clip.endMs - 100L) {
                    val delta = newStartMs - clip.startMs
                    clip.copy(
                        startMs = newStartMs.coerceAtLeast(0L),
                        trimInMs = (clip.trimInMs + delta * clip.speed).toLong().coerceAtLeast(0L)
                    )
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
    }

    /**
     * Trims a clip's end boundary
     */
    fun trimClipEnd(
        tracks: List<TimelineTrack>,
        clipId: String,
        newEndMs: Long
    ): List<TimelineTrack> {
        return tracks.map { track ->
            val updatedClips = track.clips.map { clip ->
                if (clip.id == clipId && newEndMs > clip.startMs + 100L) {
                    val delta = newEndMs - clip.endMs
                    clip.copy(
                        endMs = newEndMs,
                        trimOutMs = (clip.trimOutMs + delta * clip.speed).toLong().coerceAtLeast(0L)
                    )
                } else {
                    clip
                }
            }
            track.copy(clips = updatedClips)
        }
    }

    /**
     * Deletes a clip from a track
     */
    fun deleteClip(
        tracks: List<TimelineTrack>,
        clipId: String,
        ripple: Boolean = false
    ): List<TimelineTrack> {
        return tracks.map { track ->
            val clipToDelete = track.clips.find { it.id == clipId }
            if (clipToDelete == null) {
                track
            } else {
                val remaining = track.clips.filter { it.id != clipId }
                if (!ripple) {
                    track.copy(clips = remaining)
                } else {
                    val gapDuration = clipToDelete.durationMs
                    val rippled = remaining.map { c ->
                        if (c.startMs >= clipToDelete.endMs) {
                            c.copy(startMs = c.startMs - gapDuration, endMs = c.endMs - gapDuration)
                        } else {
                            c
                        }
                    }
                    track.copy(clips = rippled)
                }
            }
        }
    }

    /**
     * Duplicates a clip on its track, placing it right after the original
     */
    fun duplicateClip(
        tracks: List<TimelineTrack>,
        clipId: String
    ): List<TimelineTrack> {
        return tracks.map { track ->
            val clip = track.clips.find { it.id == clipId }
            if (clip == null) {
                track
            } else {
                val clone = clip.copy(
                    id = UUID.randomUUID().toString(),
                    title = "${clip.title} (Copy)",
                    startMs = clip.endMs,
                    endMs = clip.endMs + clip.durationMs
                )
                val newList = track.clips.toMutableList()
                val idx = track.clips.indexOf(clip)
                newList.add(idx + 1, clone)
                track.copy(clips = newList)
            }
        }
    }

    /**
     * Computes the overall timeline duration in milliseconds across all tracks
     */
    fun calculateTotalDurationMs(tracks: List<TimelineTrack>): Long {
        var maxMs = 1000L
        for (track in tracks) {
            for (clip in track.clips) {
                if (clip.endMs > maxMs) {
                    maxMs = clip.endMs
                }
            }
        }
        return maxMs
    }
}
