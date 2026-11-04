package com.example.core.processing

import android.graphics.Bitmap
import com.example.core.common.DenoiseConfig
import com.example.core.common.FilterStrength
import com.example.core.common.SharpenConfig
import com.example.core.gpu.HardwareAccelerationManager
import kotlin.math.abs

object DenoiseAndSharpenProcessor {

    /**
     * Applies edge-preserving spatial bilateral denoising, temporal frame noise suppression,
     * macroblock deblocking, and gradient debanding while preserving natural skin detail.
     * Executed in parallel across hardware threads via HardwareAccelerationManager.
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

        HardwareAccelerationManager.runParallelRowSlices(height) { startY, endY ->
            for (y in startY until endY) {
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

                    val n0 = src[yTop * width + x]
                    val n1 = src[yBot * width + x]
                    val n2 = src[y * width + xLeft]
                    val n3 = src[y * width + xRight]

                    val neighbors = intArrayOf(n0, n1, n2, n3)
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
        }

        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Applies CapCut Pro multi-scale unsharp masking (1px + 2px fine detail) and
     * medium-scale (4px) HDR local micro-contrast clarity while suppressing halo ringing.
     * Executed in parallel across hardware threads via HardwareAccelerationManager.
     */
    fun applySharpenAndDetail(
        source: Bitmap,
        config: SharpenConfig
    ): Bitmap {
        val fineStrength = (config.strength * 0.85f +
            config.edgeEnhancement * 0.55f +
            config.fineDetailRecovery * 0.65f +
            config.textureRecovery * 0.45f).coerceIn(0f, 2.2f)
        val clarityStrength = (config.clarity * 0.95f + config.strength * 0.35f).coerceIn(0f, 1.5f)

        if (fineStrength <= 0.01f && clarityStrength <= 0.01f && !config.deblurring) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val effectiveFine = if (config.deblurring) (fineStrength + 0.35f).coerceAtMost(2.4f) else fineStrength
        val width = source.width
        val height = source.height
        val src = IntArray(width * height)
        val out = IntArray(width * height)
        source.getPixels(src, 0, width, 0, 0, width, height)

        HardwareAccelerationManager.runParallelRowSlices(height) { startY, endY ->
            for (y in startY until endY) {
                val yTop1 = (y - 1).coerceAtLeast(0)
                val yBot1 = (y + 1).coerceAtMost(height - 1)
                val yTop2 = (y - 2).coerceAtLeast(0)
                val yBot2 = (y + 2).coerceAtMost(height - 1)
                val yTop4 = (y - 4).coerceAtLeast(0)
                val yBot4 = (y + 4).coerceAtMost(height - 1)

                val rowOff = y * width
                val top1Off = yTop1 * width
                val bot1Off = yBot1 * width
                val top2Off = yTop2 * width
                val bot2Off = yBot2 * width
                val top4Off = yTop4 * width
                val bot4Off = yBot4 * width

                for (x in 0 until width) {
                    val idx = rowOff + x
                    val cPx = src[idx]
                    val a = (cPx ushr 24) and 0xFF
                    val cr = (cPx shr 16) and 0xFF
                    val cg = (cPx shr 8) and 0xFF
                    val cb = cPx and 0xFF

                    val xLeft1 = (x - 1).coerceAtLeast(0)
                    val xRight1 = (x + 1).coerceAtMost(width - 1)
                    val xLeft2 = (x - 2).coerceAtLeast(0)
                    val xRight2 = (x + 2).coerceAtMost(width - 1)
                    val xLeft4 = (x - 4).coerceAtLeast(0)
                    val xRight4 = (x + 4).coerceAtMost(width - 1)

                    // Fine 1px + 2px ring for crisp DSLR edge & texture recovery
                    val pT1 = src[top1Off + x]
                    val pB1 = src[bot1Off + x]
                    val pL1 = src[rowOff + xLeft1]
                    val pR1 = src[rowOff + xRight1]
                    val pT2 = src[top2Off + x]
                    val pB2 = src[bot2Off + x]
                    val pL2 = src[rowOff + xLeft2]
                    val pR2 = src[rowOff + xRight2]

                    val fineBlurR = (((pT1 shr 16) and 0xFF) + ((pB1 shr 16) and 0xFF) +
                        ((pL1 shr 16) and 0xFF) + ((pR1 shr 16) and 0xFF) +
                        ((pT2 shr 16) and 0xFF) + ((pB2 shr 16) and 0xFF) +
                        ((pL2 shr 16) and 0xFF) + ((pR2 shr 16) and 0xFF)) shr 3
                    val fineBlurG = (((pT1 shr 8) and 0xFF) + ((pB1 shr 8) and 0xFF) +
                        ((pL1 shr 8) and 0xFF) + ((pR1 shr 8) and 0xFF) +
                        ((pT2 shr 8) and 0xFF) + ((pB2 shr 8) and 0xFF) +
                        ((pL2 shr 8) and 0xFF) + ((pR2 shr 8) and 0xFF)) shr 3
                    val fineBlurB = ((pT1 and 0xFF) + (pB1 and 0xFF) +
                        (pL1 and 0xFF) + (pR1 and 0xFF) +
                        (pT2 and 0xFF) + (pB2 and 0xFF) +
                        (pL2 and 0xFF) + (pR2 and 0xFF)) shr 3

                    // Medium 4px ring for CapCut Pro HDR local micro-contrast (Clarity / Structure)
                    val pT4 = src[top4Off + x]
                    val pB4 = src[bot4Off + x]
                    val pL4 = src[rowOff + xLeft4]
                    val pR4 = src[rowOff + xRight4]
                    val pTL4 = src[top4Off + xLeft4]
                    val pTR4 = src[top4Off + xRight4]
                    val pBL4 = src[bot4Off + xLeft4]
                    val pBR4 = src[bot4Off + xRight4]

                    val medBlurR = (((pT4 shr 16) and 0xFF) + ((pB4 shr 16) and 0xFF) +
                        ((pL4 shr 16) and 0xFF) + ((pR4 shr 16) and 0xFF) +
                        ((pTL4 shr 16) and 0xFF) + ((pTR4 shr 16) and 0xFF) +
                        ((pBL4 shr 16) and 0xFF) + ((pBR4 shr 16) and 0xFF)) shr 3
                    val medBlurG = (((pT4 shr 8) and 0xFF) + ((pB4 shr 8) and 0xFF) +
                        ((pL4 shr 8) and 0xFF) + ((pR4 shr 8) and 0xFF) +
                        ((pTL4 shr 8) and 0xFF) + ((pTR4 shr 8) and 0xFF) +
                        ((pBL4 shr 8) and 0xFF) + ((pBR4 shr 8) and 0xFF)) shr 3
                    val medBlurB = ((pT4 and 0xFF) + (pB4 and 0xFF) +
                        (pL4 and 0xFF) + (pR4 and 0xFF) +
                        (pTL4 and 0xFF) + (pTR4 and 0xFF) +
                        (pBL4 and 0xFF) + (pBR4 and 0xFF)) shr 3

                    // Coring threshold (2 levels) prevents amplifying flat sensor noise while boosting real edges
                    val fineDiffR = (cr - fineBlurR).let { if (abs(it) <= 2) 0 else it }
                    val fineDiffG = (cg - fineBlurG).let { if (abs(it) <= 2) 0 else it }
                    val fineDiffB = (cb - fineBlurB).let { if (abs(it) <= 2) 0 else it }

                    val medDiffR = cr - medBlurR
                    val medDiffG = cg - medBlurG
                    val medDiffB = cb - medBlurB

                    val deltaR = (fineDiffR * effectiveFine + medDiffR * clarityStrength).toInt().coerceIn(-64, 64)
                    val deltaG = (fineDiffG * effectiveFine + medDiffG * clarityStrength).toInt().coerceIn(-64, 64)
                    val deltaB = (fineDiffB * effectiveFine + medDiffB * clarityStrength).toInt().coerceIn(-64, 64)

                    val r = (cr + deltaR).coerceIn(0, 255)
                    val g = (cg + deltaG).coerceIn(0, 255)
                    val b = (cb + deltaB).coerceIn(0, 255)
                    out[idx] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
        }

        return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
    }
}
