package com.example.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.example.core.ai.LocalAiModelTask
import com.example.core.ai.OnnxAiEngine
import com.example.core.common.FilterStrength
import com.example.core.common.StabilizeConfig
import kotlin.math.abs

data class MotionVector(val dx: Float, val dy: Float, val sceneDifferenceScore: Float)

object StabilizationAndFpsProcessor {

    /**
     * Estimates global translational camera motion (dx, dy) and scene change score
     * between consecutive video frames using a fast central grid block-matching search.
     */
    fun estimateMotionVector(prevFrame: Bitmap, currFrame: Bitmap): MotionVector {
        if (prevFrame.width != currFrame.width || prevFrame.height != currFrame.height) {
            return MotionVector(0f, 0f, 1f)
        }
        val w = currFrame.width
        val h = currFrame.height
        if (w < 32 || h < 32) return MotionVector(0f, 0f, 0f)

        val searchRadius = 6
        val sampleStep = maxOf(4, minOf(w, h) / 24)
        val marginX = w / 5
        val marginY = h / 5

        var bestDx = 0
        var bestDy = 0
        var minSad = Long.MAX_VALUE
        var zeroDisplacementSad = 0L
        var samplePoints = 0

        for (dy in -searchRadius..searchRadius step 2) {
            for (dx in -searchRadius..searchRadius step 2) {
                var sad = 0L
                var count = 0
                for (y in marginY until (h - marginY) step sampleStep) {
                    val ny = (y + dy).coerceIn(0, h - 1)
                    for (x in marginX until (w - marginX) step sampleStep) {
                        val nx = (x + dx).coerceIn(0, w - 1)
                        val p1 = prevFrame.getPixel(x, y)
                        val p2 = currFrame.getPixel(nx, ny)
                        val l1 = (((p1 shr 16) and 0xFF) + ((p1 shr 8) and 0xFF) * 2 + (p1 and 0xFF)) shr 2
                        val l2 = (((p2 shr 16) and 0xFF) + ((p2 shr 8) and 0xFF) * 2 + (p2 and 0xFF)) shr 2
                        sad += abs(l1 - l2)
                        count++
                    }
                }
                if (dx == 0 && dy == 0) {
                    zeroDisplacementSad = sad
                    samplePoints = count.coerceAtLeast(1)
                }
                if (sad < minSad) {
                    minSad = sad
                    bestDx = dx
                    bestDy = dy
                }
            }
        }

        val normalizedDiff = (zeroDisplacementSad.toFloat() / (samplePoints * 255f)).coerceIn(0f, 1f)
        return MotionVector(bestDx.toFloat(), bestDy.toFloat(), normalizedDiff)
    }

    /**
     * Applies motion trajectory smoothing and crop compensation to counteract camera shake.
     */
    fun applyStabilizationWarp(
        frame: Bitmap,
        smoothedCorrectionDx: Float,
        smoothedCorrectionDy: Float,
        config: StabilizeConfig
    ): Bitmap {
        if (config.strength == FilterStrength.OFF) {
            return frame.copy(Bitmap.Config.ARGB_8888, true)
        }

        val cropPercent = config.estimatedFramingLossPercent.coerceIn(2f, 20f)
        val zoomFactor = 1f + (cropPercent / 100f)
        val w = frame.width
        val h = frame.height

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = Matrix().apply {
            postTranslate(-w / 2f, -h / 2f)
            postScale(zoomFactor, zoomFactor)
            postTranslate(
                w / 2f + smoothedCorrectionDx.coerceIn(-w * 0.08f, w * 0.08f),
                h / 2f + smoothedCorrectionDy.coerceIn(-h * 0.08f, h * 0.08f)
            )
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(frame, matrix, paint)
        return out
    }

    /**
     * Synthesizes an intermediate frame at fraction alpha (0.0 .. 1.0) between frameA and frameB.
     * Detects scene cuts first; if a cut is detected, preserves clean frame boundary without ghosting.
     * When useAiRifeModel is enabled, refines the motion-compensated frame via the local RIFE ONNX model.
     */
    fun interpolateIntermediateFrame(
        frameA: Bitmap,
        frameB: Bitmap,
        alpha: Float,
        sceneCutDetection: Boolean,
        useAiRifeModel: Boolean,
        onnxEngine: OnnxAiEngine?,
        tileSize: Int = 192
    ): Bitmap {
        val motion = estimateMotionVector(frameA, frameB)
        // Scene cut threshold: normalized luma difference > 0.34 indicates a hard scene cut
        if (sceneCutDetection && motion.sceneDifferenceScore > 0.34f) {
            return (if (alpha < 0.5f) frameA else frameB).copy(Bitmap.Config.ARGB_8888, true)
        }

        val w = frameA.width
        val h = frameA.height
        val pixelsA = IntArray(w * h)
        val pixelsB = IntArray(w * h)
        val outPixels = IntArray(w * h)
        frameA.getPixels(pixelsA, 0, w, 0, 0, w, h)
        frameB.getPixels(pixelsB, 0, w, 0, 0, w, h)

        // Bi-directional optical flow translation along estimated motion vector
        val shiftAx = (-motion.dx * alpha).toInt()
        val shiftAy = (-motion.dy * alpha).toInt()
        val shiftBx = (motion.dx * (1f - alpha)).toInt()
        val shiftBy = (motion.dy * (1f - alpha)).toInt()
        val invAlpha = 1f - alpha

        for (y in 0 until h) {
            val ay = (y + shiftAy).coerceIn(0, h - 1)
            val by = (y + shiftBy).coerceIn(0, h - 1)
            for (x in 0 until w) {
                val ax = (x + shiftAx).coerceIn(0, w - 1)
                val bx = (x + shiftBx).coerceIn(0, w - 1)

                val pA = pixelsA[ay * w + ax]
                val pB = pixelsB[by * w + bx]

                val r = (((pA shr 16) and 0xFF) * invAlpha + ((pB shr 16) and 0xFF) * alpha).toInt().coerceIn(0, 255)
                val g = (((pA shr 8) and 0xFF) * invAlpha + ((pB shr 8) and 0xFF) * alpha).toInt().coerceIn(0, 255)
                val b = ((pA and 0xFF) * invAlpha + (pB and 0xFF) * alpha).toInt().coerceIn(0, 255)
                outPixels[y * w + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val blended = Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
        if (useAiRifeModel && onnxEngine != null) {
            val refined = onnxEngine.runTiledInference(
                source = blended,
                task = LocalAiModelTask.FRAME_INTERPOLATION,
                tileSize = tileSize,
                overlap = 12
            )
            blended.recycle()
            return refined
        }
        return blended
    }
}
