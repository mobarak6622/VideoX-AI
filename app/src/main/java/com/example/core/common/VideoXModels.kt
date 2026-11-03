package com.example.core.common

import kotlin.math.roundToInt

enum class TargetResolution(
    val label: String,
    val shortBadge: String,
    val targetLongEdge: Int,
    val targetShortEdge: Int
) {
    ORIGINAL("Original Resolution", "ORIG", 0, 0),
    HD_720P("720p HD (1280×720)", "720p", 1280, 720),
    FHD_1080P("1080p Full HD (1920×1080)", "1080p", 1920, 1080),
    QHD_1440P("1440p QHD (2560×1440)", "1440p", 2560, 1440),
    UHD_4K("2160p 4K UHD (3840×2160)", "4K UHD", 3840, 2160);

    fun computeOutputDimensions(inputWidth: Int, inputHeight: Int): Pair<Int, Int> {
        if (this == ORIGINAL || inputWidth <= 0 || inputHeight <= 0) {
            return Pair(makeEven(inputWidth.coerceAtLeast(16)), makeEven(inputHeight.coerceAtLeast(16)))
        }
        val isPortrait = inputHeight > inputWidth
        val aspect = inputWidth.toDouble() / inputHeight.toDouble()
        val (outW, outH) = if (isPortrait) {
            val h = targetLongEdge
            val w = (h * aspect).roundToInt().coerceAtLeast(16)
            Pair(w, h)
        } else {
            val w = targetLongEdge
            val h = (w / aspect).roundToInt().coerceAtLeast(16)
            Pair(w, h)
        }
        return Pair(makeEven(outW), makeEven(outH))
    }

    companion object {
        fun makeEven(value: Int): Int = if (value % 2 == 0) value else value + 1
    }
}

enum class UpscaleMode(val title: String, val subtitle: String) {
    STANDARD_RESIZE(
        "Standard Upscale",
        "High-precision bicubic resampling with GPU-friendly filtering (No fake AI claim)"
    ),
    AI_SUPER_RESOLUTION(
        "AI Upscale (ONNX Neural SR)",
        "Tiled neural network Super Resolution via local ONNX Runtime Mobile"
    ),
    MAXIMUM_QUALITY(
        "Maximum Quality (Multi-Pass AI + Edge Recovery)",
        "Overlapping tiled ONNX inference with micro-contrast & sub-pixel edge reconstruction"
    )
}

enum class ColorPreset(
    val displayName: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val temperature: Float,
    val tint: Float,
    val highlights: Float,
    val shadows: Float,
    val vibrance: Float
) {
    NONE("Manual / Custom", 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    NATURAL("Natural Balanced", 0.03f, 0.08f, 0.06f, 0.0f, 0.0f, -0.05f, 0.08f, 0.12f),
    CINEMATIC("Cinematic Teal & Gold", -0.02f, 0.18f, -0.05f, -0.08f, 0.04f, -0.12f, 0.10f, 0.15f),
    VIVID("Vivid HDR Pop", 0.04f, 0.15f, 0.25f, 0.04f, 0.0f, -0.08f, 0.12f, 0.28f),
    WARM("Golden Hour Warm", 0.03f, 0.10f, 0.12f, 0.24f, 0.05f, -0.04f, 0.06f, 0.14f),
    COOL("Nordic Sapphire Cool", 0.01f, 0.12f, -0.04f, -0.24f, -0.04f, -0.06f, 0.05f, 0.10f),
    PORTRAIT("Skin-Safe Portrait", 0.05f, 0.06f, 0.04f, 0.08f, 0.03f, -0.10f, 0.14f, 0.08f),
    LANDSCAPE("Crisp Landscape", 0.02f, 0.20f, 0.18f, 0.02f, -0.02f, -0.15f, 0.15f, 0.22f),
    FILM("Analog Kodak Film", 0.01f, 0.14f, -0.08f, 0.10f, 0.04f, -0.18f, 0.16f, 0.06f),
    PROFESSIONAL("Studio Broadcast Rec.709", 0.02f, 0.12f, 0.08f, 0.0f, 0.0f, -0.08f, 0.08f, 0.10f)
}

enum class FpsConversionMode(val title: String, val description: String) {
    STANDARD_RESAMPLE(
        "Standard FPS Conversion",
        "Exact timestamp resampling with deterministic frame dropping/duplication"
    ),
    AI_OPTICAL_FLOW_RIFE(
        "AI Frame Interpolation (Optical Flow + RIFE ONNX)",
        "Block motion estimation + scene-cut detection + ONNX intermediate frame synthesis"
    )
}

enum class FilterStrength(val label: String, val factor: Float) {
    OFF("Off", 0f),
    LOW("Low", 0.35f),
    MEDIUM("Medium", 0.65f),
    HIGH("High", 0.95f),
    CUSTOM("Custom", 0.5f)
}

enum class PreviewQuality(val label: String, val maxPreviewDimension: Int) {
    DRAFT("Draft (Fast 480p)", 480),
    BALANCED("Balanced (720p)", 720),
    HIGH("High (1080p Full)", 1080)
}

enum class VideoCodecOption(val label: String, val mimeType: String) {
    H264("H.264 / AVC (Universal)", "video/avc"),
    H265_HEVC("H.265 / HEVC (High Efficiency 4K)", "video/hevc"),
    AV1("AV1 (Next-Gen Ultra Compression)", "video/av01")
}

enum class AudioExportMode(val label: String) {
    PRESERVE_ORIGINAL("Preserve Original Audio Stream (Lossless Sync)"),
    AAC_REENCODE("AAC Stereo High-Fidelity"),
    MUTE("Mute Audio (Video Track Only)")
}

enum class BitratePreset(val label: String, val multiplier: Float) {
    AUTO("Automatic (Smart Quality Bitrate)", 1.0f),
    LOW("Low (Compact File Size)", 0.55f),
    MEDIUM("Medium (Balanced Stream)", 0.85f),
    HIGH("High (Cinema Archival)", 1.45f),
    CUSTOM("Custom Mbps", 1.0f)
}

enum class ProcessingProfile(val label: String, val description: String, val defaultTileSize: Int) {
    BATTERY_SAVER("Battery Saver", "Lower thermal footprint, 128px tiles, conservative threads", 128),
    BALANCED("Balanced", "Adaptive CPU/GPU threading, 192px tiles, thermal guard active", 192),
    HIGH_PERFORMANCE("High Performance", "Multi-threaded pipeline, 256px tiles, fast queue", 256),
    MAXIMUM_QUALITY("Maximum Quality", "High-overlap 256px tiles, full precision color + detail passes", 256)
}

data class VideoMetadata(
    val uriString: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Float,
    val videoCodec: String,
    val audioCodec: String,
    val bitrateBps: Long,
    val aspectRatioLabel: String,
    val rotationDegrees: Int,
    val isHdr: Boolean,
    val hasAudio: Boolean,
    val isSupported: Boolean = true,
    val unsupportedReason: String? = null
) {
    val resolutionBadge: String
        get() {
            val longEdge = maxOf(width, height)
            return when {
                longEdge >= 3800 -> "4K UHD"
                longEdge >= 2500 -> "1440p QHD"
                longEdge >= 1900 -> "1080p FHD"
                longEdge >= 1200 -> "720p HD"
                else -> "${width}×${height}"
            }
        }

    val estimatedTotalFrames: Int
        get() = ((durationMs / 1000.0) * frameRate.coerceAtLeast(1f)).roundToInt().coerceAtLeast(1)
}

data class UpscaleConfig(
    val targetResolution: TargetResolution = TargetResolution.ORIGINAL,
    val mode: UpscaleMode = UpscaleMode.STANDARD_RESIZE,
    val tileSize: Int = 192,
    val tileOverlap: Int = 16
)

data class ColorConfig(
    val enabled: Boolean = false,
    val autoColor: Boolean = false,
    val preset: ColorPreset = ColorPreset.NONE,
    val intensityPercent: Float = 100f,
    val exposure: Float = 0f,       // -1f..1f
    val brightness: Float = 0f,     // -0.5f..0.5f
    val contrast: Float = 0f,       // -0.5f..0.5f
    val saturation: Float = 0f,     // -1f..1f
    val whiteBalance: Float = 0f,   // -1f..1f
    val temperature: Float = 0f,    // -1f..1f
    val tint: Float = 0f,           // -1f..1f
    val highlights: Float = 0f,     // -1f..1f
    val shadows: Float = 0f,        // -1f..1f
    val gamma: Float = 1.0f,        // 0.5f..2.0f
    val blackLevel: Float = 0f,     // -0.2f..0.2f
    val whiteLevel: Float = 0f,     // -0.2f..0.2f
    val vibrance: Float = 0f        // -1f..1f
)

data class FpsConfig(
    val targetFps: Int = 0, // 0 = Original
    val mode: FpsConversionMode = FpsConversionMode.STANDARD_RESAMPLE,
    val sceneCutDetection: Boolean = true,
    val preserveDuration: Boolean = true
)

data class DenoiseConfig(
    val strength: FilterStrength = FilterStrength.OFF,
    val spatialStrength: Float = 0.4f,
    val temporalStrength: Float = 0.4f,
    val lowLightNoiseReduction: Boolean = true,
    val grainRemoval: Boolean = false,
    val compressionArtifactReduction: Boolean = true,
    val debanding: Boolean = true,
    val preserveSkinAndEdges: Boolean = true
)

data class StabilizeConfig(
    val strength: FilterStrength = FilterStrength.OFF,
    val smoothingRadiusFrames: Int = 8,
    val cropCompensationPercent: Float = 6f,
    val rollingShutterCorrection: Boolean = false
) {
    val estimatedFramingLossPercent: Float
        get() = when (strength) {
            FilterStrength.OFF -> 0f
            FilterStrength.LOW -> 4.0f
            FilterStrength.MEDIUM -> 7.5f
            FilterStrength.HIGH -> 12.0f
            FilterStrength.CUSTOM -> cropCompensationPercent
        }
}

data class SharpenConfig(
    val strength: Float = 0f, // 0f..1f
    val edgeEnhancement: Float = 0f,
    val clarity: Float = 0f,
    val fineDetailRecovery: Float = 0f,
    val textureRecovery: Float = 0f,
    val deblurring: Boolean = false
)

data class ExportConfig(
    val resolution: TargetResolution = TargetResolution.ORIGINAL,
    val targetFps: Int = 0, // 0 = Original
    val codec: VideoCodecOption = VideoCodecOption.H264,
    val audioMode: AudioExportMode = AudioExportMode.PRESERVE_ORIGINAL,
    val audioBitrateKbps: Int = 192,
    val bitratePreset: BitratePreset = BitratePreset.AUTO,
    val customBitrateMbps: Float = 16f
)

data class DslrBokehConfig(
    val enabled: Boolean = false,
    val aperture: String = "f/1.8", // f/1.2, f/1.4, f/1.8, f/2.8, f/4.0
    val blurRadius: Float = 0.5f,   // 0f..1f
    val focalDistance: Float = 0.45f, // 0f..1f (normalized subject plane)
    val depthFalloff: Float = 0.6f,   // smoothness of depth transition
    val bokehHighlightBoost: Float = 0.35f, // specular bokeh circle glow
    val portraitIsolation: Boolean = true
)

data class AiDirectorConfig(
    val cloudAiEnabled: Boolean = true,
    val activeGenrePreset: String = "Auto Detect (CapCut Pro AI)",
    val dslrLightingEnhanced: Boolean = true,
    val cinematicDepthOfField: Boolean = true,
    val directorNotes: String = ""
)

data class EnhancementPipelineState(
    val upscale: UpscaleConfig = UpscaleConfig(),
    val color: ColorConfig = ColorConfig(),
    val fps: FpsConfig = FpsConfig(),
    val denoise: DenoiseConfig = DenoiseConfig(),
    val stabilize: StabilizeConfig = StabilizeConfig(),
    val sharpen: SharpenConfig = SharpenConfig(),
    val bokeh: DslrBokehConfig = DslrBokehConfig(),
    val aiDirector: AiDirectorConfig = AiDirectorConfig(),
    val export: ExportConfig = ExportConfig(),
    val previewQuality: PreviewQuality = PreviewQuality.BALANCED
)
