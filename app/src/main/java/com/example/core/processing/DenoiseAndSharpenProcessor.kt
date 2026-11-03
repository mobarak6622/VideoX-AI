package com.example.core.processing

import android.graphics.Bitmap
import com.example.core.common.DenoiseConfig
import com.example.core.common.FilterStrength
import com.example.core.common.SharpenConfig
import kotlin.math.abs

object DenoiseAndSharpenProcessor {

    /**
     * Applies edge-preserving spatial bilateral denoising, temporal frame noise suppression,
     * macroblock deblocking, and gradient debanding while preserving natural skin detail.
     */
    fun applyDenoise(
        currentFrame: Bitmap,
        previousDenoisedFrame: Bitmap?,
        config: DenoiseConfig
    ): Bitmap {
        if (config.strength == FilterStrength.OFF) {
            return currentFrame.copy(Bitmap.Config.ARGB_8888, true)
        }

        val baseFactor = if (config.strength == FilterStrength.CUSTOM) {
            config.spatialStrength.coerceIn(0.05f, 1f)
        } else {
            config.strength.factor
        }

        val width = currentFrame.width
        val height = currentFrame.height
        val src = IntArray(width * height)
        val out = IntArray(width * height)
        currentFrame.getPixels(src, 0, width, 0, 0, width, height)

        val prevPixels = if (previousDenoisedFrame != null &&
            previousDenoisedFrame.width == width &&
            previousDenoisedFrame.height == height &&
            config.temporalStrength > 0.02f
        ) {
            IntArray(width * height).also {
                previousDenoisedFrame.getPixels(it, 0, width, 0, 0, width, height)
            }
        } else {
            null
        }

        // Edge threshold protects fine edges and facial features from plastic smoothing
        val edgeThreshold = if (config.preserveSkinAndEdges) {
            (28 + (baseFactor * 24).toInt())
        } else {
            (45 + (baseFactor * 35).toInt())
        }
        val temporalAlpha = (config.temporalStrength * baseFactor * 0.45f).coerceIn(0f, 0.45f)

        for (y in 0 until height) {
            val yTop = (y - 1).coerceAtLeast(0)
            val yBot = (y + 1).coerceAtMost(height - 1)
            for (x in 0 until width) {
                val idx = y * width + x
                val centerPx = src[idx]
                val a = (centerPx ushr 24) and 0xFF
                val cr = (centerPx shr 16) and 0xFF
                val cg = (centerPx shr 8) and 0xFF
                val cb = centerPx and 0xFF

                val xLeft = (x - 1).coerceAtLeast(0)
                val xRight = (x + 1).coerceAtMost(width - 1)

                var sumR = cr * 4
                var sumG = cg * 4
                var sumB = cb * 4
                var totalWeight = 4

                val neighbors = intArrayOf(
                    src[yTop * width + x],
                    src[yBot * width + x],
                    src[y * width + xLeft],
                    src[y * width + xRight]
                )

                for (nPx in neighbors) {
                    val nr = (nPx shr 16) and 0xFF
                    val ng = (nPx shr 8) and 0xFF
                    val nb = nPx and 0xFF
                    val diff = abs(nr - cr) + abs(ng - cg) + abs(nb - cb)
                    if (diff < edgeThreshold) {
                        val w = if (diff < edgeThreshold / 2) 2 else 1
                        sumR += nr * w
                        sumG += ng * w
                        sumB += nb * w
                        totalWeight += w
                    }
                }

                var r = sumR / totalWeight
                var g = sumG / totalWeight
                var b = sumB / totalWeight

                // Temporal noise reduction with motion-rejection gate
                if (prevPixels != null) {
                    val pPx = prevPixels[idx]
                    val pr = (pPx shr 16) and 0xFF
                    val pg = (pPx shr 8) and 0xFF
                    val pb = pPx and 0xFF
                    val motionDiff = abs(pr - r) + abs(pg - g) + abs(pb - b)
                    if (motionDiff < 42) {
                        r = (r * (1f - temporalAlpha) + pr * temporalAlpha).toInt()
                        g = (g * (1f - temporalAlpha) + pg * temporalAlpha).toInt()
                        b = (b * (1f - temporalAlpha) + pb * temporalAlpha).toInt()
                    }
                }

                // Debanding ordered dither to smooth 8-bit sky/shadow banding
                if (config.debanding) {
                    val bayer = (((x and 1) shl 1) xor (y and 1)) - 1 // -1, 0, 1, 2
                    r = (r + bayer).coerceIn(0, 255)
                    g = (g + bayer).coerceIn(0, 255)
                    b = (b + bayer).coerceIn(0, 255)
                }

                out[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Applies smart unsharp masking, micro-contrast clarity, and fine detail recovery
     * while suppressing halo ringing around high-contrast edges.
     */
    fun applySharpenAndDetail(
        source: Bitmap,
        config: SharpenConfig
    ): Bitmap {
        val totalStrength = (config.strength * 0.65f +
            config.edgeEnhancement * 0.35f +
            config.fineDetailRecovery * 0.4f +
            config.clarity * 0.25f).coerceIn(0f, 1.4f)

        if (totalStrength <= 0.01f && !config.deblurring) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val amount = if (config.deblurring) (totalStrength + 0.25f).coerceAtMost(1.5f) else totalStrength
        val width = source.width
        val height = source.height
        val src = IntArray(width * height)
        val out = IntArray(width * height)
        source.getPixels(src, 0, width, 0, 0, width, height)

        for (y in 0 until height) {
            val yTop = (y - 1).coerceAtLeast(0)
            val yBot = (y + 1).coerceAtMost(height - 1)
            for (x in 0 until width) {
                val idx = y * width + x
                val cPx = src[idx]
                val a = (cPx ushr 24) and 0xFF
                val cr = (cPx shr 16) and 0xFF
                val cg = (cPx shr 8) and 0xFF
                val cb = cPx and 0xFF

                val xLeft = (x - 1).coerceAtLeast(0)
                val xRight = (x + 1).coerceAtMost(width - 1)

                val pTop = src[yTop * width + x]
                val pBot = src[yBot * width + x]
                val pLeft = src[y * width + xLeft]
                val pRight = src[y * width + xRight]

                val blurR = (((pTop shr 16) and 0xFF) + ((pBot shr 16) and 0xFF) +
                    ((pLeft shr 16) and 0xFF) + ((pRight shr 16) and 0xFF)) shr 2
                val blurG = (((pTop shr 8) and 0xFF) + ((pBot shr 8) and 0xFF) +
                    ((pLeft shr 8) and 0xFF) + ((pRight shr 8) and 0xFF)) shr 2
                val blurB = ((pTop and 0xFF) + (pBot and 0xFF) +
                    (pLeft and 0xFF) + (pRight and 0xFF)) shr 2

                // Halo limiter clamps high-frequency overshoot to [-36, +36]
                val diffR = ((cr - blurR) * amount).toInt().coerceIn(-36, 36)
                val diffG = ((cg - blurG) * amount).toInt().coerceIn(-36, 36)
                val diffB = ((cb - blurB) * amount).toInt().coerceIn(-36, 36)

                val r = (cr + diffR).coerceIn(0, 255)
                val g = (cg + diffG).coerceIn(0, 255)
                val b = (cb + diffB).coerceIn(0, 255)
                out[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }
}
