package com.example.core.processing

import android.graphics.Bitmap
import com.example.core.common.ColorConfig
import com.example.core.common.ColorPreset
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

data class AutoColorEstimate(
    val exposure: Float,
    val contrast: Float,
    val saturation: Float,
    val temperature: Float,
    val tint: Float,
    val shadows: Float,
    val highlights: Float
)

object ColorCorrectionProcessor {

    /**
     * Analyzes a representative frame's luminance histogram and RGB channel balance
     * to estimate optimal Auto Color parameters while protecting skin tones.
     */
    fun analyzeFrameForAutoColor(bitmap: Bitmap): AutoColorEstimate {
        val w = bitmap.width
        val h = bitmap.height
        val step = max(1, min(w, h) / 64)
        var sumLuma = 0.0
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var sumSat = 0.0
        var darkCount = 0
        var brightCount = 0
        var sampleCount = 0

        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val px = bitmap.getPixel(x, y)
                val r = ((px shr 16) and 0xFF) / 255.0
                val g = ((px shr 8) and 0xFF) / 255.0
                val b = (px and 0xFF) / 255.0
                val luma = 0.2126 * r + 0.7152 * g + 0.0722 * b
                val maxC = max(r, max(g, b))
                val minC = min(r, min(g, b))
                val sat = if (maxC > 1e-4) (maxC - minC) / maxC else 0.0

                sumLuma += luma
                sumR += r
                sumG += g
                sumB += b
                sumSat += sat
                if (luma < 0.18) darkCount++
                if (luma > 0.85) brightCount++
                sampleCount++
            }
        }

        if (sampleCount == 0) {
            return AutoColorEstimate(0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }

        val meanLuma = (sumLuma / sampleCount).toFloat()
        val meanR = (sumR / sampleCount).toFloat()
        val meanG = (sumG / sampleCount).toFloat()
        val meanB = (sumB / sampleCount).toFloat()
        val meanSat = (sumSat / sampleCount).toFloat()

        // Target balanced mid-luma ~0.48
        val estExposure = ((0.48f - meanLuma) * 0.85f).coerceIn(-0.45f, 0.45f)
        val darkRatio = darkCount.toFloat() / sampleCount
        val brightRatio = brightCount.toFloat() / sampleCount

        val estShadows = if (darkRatio > 0.25f) ((darkRatio - 0.2f) * 0.45f).coerceIn(0f, 0.3f) else 0.04f
        val estHighlights = if (brightRatio > 0.15f) (-(brightRatio - 0.1f) * 0.45f).coerceIn(-0.3f, 0f) else -0.04f
        val estContrast = if (darkRatio < 0.08f && brightRatio < 0.08f) 0.14f else 0.07f
        val estSat = ((0.38f - meanSat) * 0.4f).coerceIn(-0.1f, 0.22f)

        // Gray-world white balance with gentle clamp
        val estTemp = ((meanB - meanR) * 0.45f).coerceIn(-0.25f, 0.25f)
        val estTint = ((meanG - (meanR + meanB) * 0.5f) * 0.35f).coerceIn(-0.2f, 0.2f)

        return AutoColorEstimate(
            exposure = estExposure,
            contrast = estContrast,
            saturation = estSat,
            temperature = estTemp,
            tint = estTint,
            shadows = estShadows,
            highlights = estHighlights
        )
    }

    /**
     * Applies the 14-axis local color engine to a video frame with skin-tone protection
     * and adjustable 0%..100% intensity blending.
     */
    fun processFrame(
        source: Bitmap,
        config: ColorConfig,
        autoEstimate: AutoColorEstimate? = null
    ): Bitmap {
        val intensity = (config.intensityPercent / 100f).coerceIn(0f, 1f)
        if ((!config.enabled && !config.autoColor && config.preset == ColorPreset.NONE) || intensity <= 0.001f) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val preset = config.preset
        val auto = if (config.autoColor) (autoEstimate ?: analyzeFrameForAutoColor(source)) else null

        val exp = (config.exposure + (auto?.exposure ?: 0f)) * intensity
        val brt = (config.brightness + preset.brightness) * intensity
        val con = (config.contrast + preset.contrast + (auto?.contrast ?: 0f)) * intensity
        val sat = (config.saturation + preset.saturation + (auto?.saturation ?: 0f)) * intensity
        val temp = (config.temperature + config.whiteBalance * 0.5f + preset.temperature + (auto?.temperature ?: 0f)) * intensity
        val tint = (config.tint + preset.tint + (auto?.tint ?: 0f)) * intensity
        val high = (config.highlights + preset.highlights + (auto?.highlights ?: 0f)) * intensity
        val shad = (config.shadows + preset.shadows + (auto?.shadows ?: 0f)) * intensity
        val vib = (config.vibrance + preset.vibrance) * intensity
        val blk = config.blackLevel * intensity
        val wht = config.whiteLevel * intensity
        val invGamma = (1f / config.gamma.coerceIn(0.5f, 2.0f))

        val expMultiplier = 2.0f.pow(exp)
        val contrastFactor = (1f + con).coerceIn(0.2f, 2.5f)

        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val px = pixels[i]
            val a = (px ushr 24) and 0xFF
            var r = ((px shr 16) and 0xFF) / 255f
            var g = ((px shr 8) and 0xFF) / 255f
            var b = (px and 0xFF) / 255f

            // Detect skin-tone proximity to prevent unnatural orange/magenta faces
            val isSkinTone = r > 0.35f && g > 0.2f && b > 0.12f && r > g && g > b && (r - g) in 0.04f..0.32f
            val skinProtection = if (isSkinTone) 0.55f else 1.0f

            // 1. Exposure & Black/White level
            r = (r * expMultiplier + brt + blk) * (1f + wht)
            g = (g * expMultiplier + brt + blk) * (1f + wht)
            b = (b * expMultiplier + brt + blk) * (1f + wht)

            // 2. Shadows & Highlights recovery
            val luma0 = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val shadowWeight = (1f - luma0).coerceIn(0f, 1f).let { it * it }
            val highlightWeight = luma0.coerceIn(0f, 1f).let { it * it }
            val toneDelta = shad * shadowWeight * 0.45f + high * highlightWeight * 0.45f
            r += toneDelta
            g += toneDelta
            b += toneDelta

            // 3. Contrast & Gamma
            r = ((r - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)
            g = ((g - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)
            b = ((b - 0.5f) * contrastFactor + 0.5f).coerceIn(0f, 1f)

            if (abs(invGamma - 1f) > 0.01f) {
                r = r.pow(invGamma)
                g = g.pow(invGamma)
                b = b.pow(invGamma)
            }

            // 4. White Balance Temperature & Tint
            r += temp * 0.16f * skinProtection
            b -= temp * 0.16f * skinProtection
            g -= tint * 0.12f * skinProtection

            // 5. Saturation & Skin-Safe Vibrance
            val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
            val maxCh = max(r, max(g, b))
            val minCh = min(r, min(g, b))
            val currentSat = (maxCh - minCh).coerceIn(0f, 1f)
            // Vibrance boosts muted colors more than already-saturated colors
            val vibranceBoost = vib * (1f - currentSat) * skinProtection
            val totalSatMultiplier = (1f + sat * skinProtection + vibranceBoost).coerceIn(0f, 2.2f)

            r = (luma + (r - luma) * totalSatMultiplier).coerceIn(0f, 1f)
            g = (luma + (g - luma) * totalSatMultiplier).coerceIn(0f, 1f)
            b = (luma + (b - luma) * totalSatMultiplier).coerceIn(0f, 1f)

            val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
            val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
            val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
            pixels[i] = (a shl 24) or (ri shl 16) or (gi shl 8) or bi
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
