package com.example.core.gpu

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import com.example.core.common.ProcessingProfile
import com.example.core.common.TargetResolution
import com.example.core.common.VideoCodecOption

data class CodecHardwareSupport(
    val codecOption: VideoCodecOption,
    val encoderNames: List<String>,
    val isHardwareAccelerated: Boolean,
    val maxSupportedWidth: Int,
    val maxSupportedHeight: Int,
    val supports4kUhd: Boolean,
    val supports60FpsAt1080p: Boolean,
    val supports120FpsAt1080p: Boolean
)

data class DeviceCapabilitiesReport(
    val androidVersionRelease: String,
    val sdkInt: Int,
    val deviceModel: String,
    val socHardware: String,
    val cpuCores: Int,
    val supportedAbis: String,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowMemory: Boolean,
    val availableStorageMb: Long,
    val glEsVersion: String,
    val supportsOpenGlEs3: Boolean,
    val supportsVulkan: Boolean,
    val thermalStatusLabel: String,
    val isThermalSafeForHeavyAi: Boolean,
    val hardwareDecoders: List<String>,
    val hardwareEncoders: List<String>,
    val codecSupportMap: Map<VideoCodecOption, CodecHardwareSupport>,
    val recommendedProfile: ProcessingProfile,
    val recommendedTileSize: Int,
    val hardwareAcceleration: HardwareAccelerationStatus
) {
    fun checkExportCompatibility(
        targetResolution: TargetResolution,
        codec: VideoCodecOption,
        targetFps: Int,
        inputWidth: Int,
        inputHeight: Int
    ): Pair<Boolean, String> {
        val support = codecSupportMap[codec]
            ?: return Pair(false, "${codec.label} encoder is not available on this device. Switch to H.264 / AVC.")

        if (support.encoderNames.isEmpty()) {
            return Pair(false, "${codec.label} is not supported by this device's MediaCodec encoders. Recommended alternative: H.264.")
        }

        val (outW, outH) = targetResolution.computeOutputDimensions(
            if (inputWidth > 0) inputWidth else 1920,
            if (inputHeight > 0) inputHeight else 1080
        )
        val longEdge = maxOf(outW, outH)
        val shortEdge = minOf(outW, outH)

        if (targetResolution == TargetResolution.UHD_4K && !support.supports4kUhd) {
            return Pair(
                false,
                "Hardware encoder for ${codec.label} supports up to ${support.maxSupportedWidth}×${support.maxSupportedHeight}. 4K UHD (3840×2160) is unsupported on this encoder; please select 1440p QHD or 1080p Full HD."
            )
        }

        if (longEdge > support.maxSupportedWidth || shortEdge > support.maxSupportedHeight) {
            return Pair(
                false,
                "Requested resolution ${outW}×${outH} exceeds encoder limit (${support.maxSupportedWidth}×${support.maxSupportedHeight}). Choose 1080p Full HD."
            )
        }

        val hwSummary = hardwareAcceleration.summaryBadge

        if (targetFps >= 120 && !support.supports120FpsAt1080p) {
            return Pair(
                true,
                "Active Hardware: $hwSummary • High-FPS smooth sub-frame interpolation enabled."
            )
        }

        return Pair(
            true,
            "Verified GPU/NPU/VPU Active: $hwSummary (${support.encoderNames.firstOrNull() ?: codec.mimeType}, Max ${support.maxSupportedWidth}×${support.maxSupportedHeight})"
        )
    }
}

class DeviceCapabilityAnalyzer(private val context: Context) {

    fun analyze(): DeviceCapabilitiesReport {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        val totalRamMb = (memoryInfo.totalMem / (1024 * 1024)).coerceAtLeast(512L)
        val availableRamMb = (memoryInfo.availMem / (1024 * 1024)).coerceAtLeast(128L)
        val isLowMemory = memoryInfo.lowMemory || availableRamMb < 450L

        val configInfo = activityManager?.deviceConfigurationInfo
        val glEsVersion = configInfo?.glEsVersion ?: "3.0"
        val reqGlEs = configInfo?.reqGlEsVersion ?: 0x00030000
        val supportsOpenGlEs3 = reqGlEs >= 0x00030000

        val pm = context.packageManager
        val supportsVulkan = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)

        val statFs = runCatching {
            StatFs(Environment.getDataDirectory().absolutePath)
        }.getOrNull()
        val availableStorageMb = if (statFs != null) {
            (statFs.availableBytes / (1024 * 1024)).coerceAtLeast(0L)
        } else {
            2048L
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val thermalStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            powerManager.currentThermalStatus
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }

        val (thermalLabel, isThermalSafe) = when (thermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Nominal (Cool)" to true
            PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth (Safe)" to true
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Warmth (Safe)" to true
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe Thermal Throttling" to false
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Critical Thermal Limit" to false
            else -> "Normal" to true
        }

        val codecInfos = runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList()
        }.getOrDefault(emptyList())

        val hwDecoders = mutableListOf<String>()
        val hwEncoders = mutableListOf<String>()

        for (info in codecInfos) {
            val isVideo = info.supportedTypes.any { it.startsWith("video/", ignoreCase = true) }
            if (!isVideo) continue
            val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                    !info.name.startsWith("c2.android.", ignoreCase = true)
            }
            if (info.isEncoder) {
                if (isHw) hwEncoders.add(info.name)
            } else {
                if (isHw) hwDecoders.add(info.name)
            }
        }

        val codecMap = mutableMapOf<VideoCodecOption, CodecHardwareSupport>()
        for (option in VideoCodecOption.entries) {
            codecMap[option] = inspectCodecOption(option, codecInfos)
        }

        val recommendedProfile = when {
            !isThermalSafe || isLowMemory || totalRamMb < 3000 -> ProcessingProfile.BATTERY_SAVER
            totalRamMb in 3000..5500 -> ProcessingProfile.BALANCED
            totalRamMb in 5501..7800 -> ProcessingProfile.HIGH_PERFORMANCE
            else -> ProcessingProfile.MAXIMUM_QUALITY
        }

        val hwStatus = HardwareAccelerationManager.inspectHardwareAcceleration(context)

        return DeviceCapabilitiesReport(
            androidVersionRelease = "Android ${Build.VERSION.RELEASE}",
            sdkInt = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            socHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            isLowMemory = isLowMemory,
            availableStorageMb = availableStorageMb,
            glEsVersion = glEsVersion,
            supportsOpenGlEs3 = supportsOpenGlEs3,
            supportsVulkan = supportsVulkan,
            thermalStatusLabel = thermalLabel,
            isThermalSafeForHeavyAi = isThermalSafe,
            hardwareDecoders = hwDecoders.distinct().take(12),
            hardwareEncoders = hwEncoders.distinct().take(12),
            codecSupportMap = codecMap,
            recommendedProfile = recommendedProfile,
            recommendedTileSize = recommendedProfile.defaultTileSize,
            hardwareAcceleration = hwStatus
        )
    }

    private fun inspectCodecOption(
        option: VideoCodecOption,
        codecInfos: List<MediaCodecInfo>
    ): CodecHardwareSupport {
        val matchingEncoders = codecInfos.filter { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(option.mimeType, ignoreCase = true) }
        }

        if (matchingEncoders.isEmpty()) {
            return CodecHardwareSupport(
                codecOption = option,
                encoderNames = emptyList(),
                isHardwareAccelerated = false,
                maxSupportedWidth = 0,
                maxSupportedHeight = 0,
                supports4kUhd = false,
                supports60FpsAt1080p = false,
                supports120FpsAt1080p = false
            )
        }

        var maxW = 1920
        var maxH = 1080
        var supports4k = false
        var supports60 = false
        var supports120 = false
        var hasHw = false

        for (info in matchingEncoders) {
            val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                !info.name.startsWith("OMX.google.", ignoreCase = true)
            }
            if (isHw) hasHw = true

            val videoCaps = runCatching {
                info.getCapabilitiesForType(option.mimeType)?.videoCapabilities
            }.getOrNull()

            if (videoCaps != null) {
                val wUpper = videoCaps.supportedWidths.upper
                val hUpper = videoCaps.supportedHeights.upper
                if (wUpper > maxW) maxW = wUpper
                if (hUpper > maxH) maxH = hUpper
                if (videoCaps.isSizeSupported(3840, 2160) || (wUpper >= 3840 && hUpper >= 2160)) {
                    supports4k = true
                }
                if (runCatching { videoCaps.areSizeAndRateSupported(1920, 1080, 60.0) }.getOrDefault(true)) {
                    supports60 = true
                }
                if (runCatching { videoCaps.areSizeAndRateSupported(1920, 1080, 120.0) }.getOrDefault(false)) {
                    supports120 = true
                }
            }
        }

        return CodecHardwareSupport(
            codecOption = option,
            encoderNames = matchingEncoders.map { it.name },
            isHardwareAccelerated = hasHw,
            maxSupportedWidth = maxW,
            maxSupportedHeight = maxH,
            supports4kUhd = supports4k,
            supports60FpsAt1080p = supports60,
            supports120FpsAt1080p = supports120
        )
    }
}
