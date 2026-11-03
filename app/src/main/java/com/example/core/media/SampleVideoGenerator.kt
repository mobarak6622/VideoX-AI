package com.example.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates a real, playable MP4 video clip on-device using MediaCodecVideoEncoder.
 * Includes moving cinema color targets, skin-tone patch, simulated camera shake, and low-light grain
 * so the user can test every enhancement pipeline (AI Upscale, Auto Color, Denoise, Stabilization,
 * FPS Interpolation, and 4K Export) immediately offline.
 */
class SampleVideoGenerator(private val context: Context) {

    fun generateSampleMp4(
        width: Int = 848,
        height: Int = 480,
        fps: Int = 24,
        durationSeconds: Int = 2,
        fileName: String = "VideoX_Studio_Sample_480p.mp4"
    ): Result<Uri> {
        return runCatching {
            val outDir = File(context.filesDir, "sample_videos").apply { mkdirs() }
            val outFile = File(outDir, fileName)
            if (outFile.exists()) outFile.delete()

            val alignedW = MediaCodecVideoEncoder.alignTo16(width)
            val alignedH = MediaCodecVideoEncoder.alignTo16(height)
            val totalFrames = (fps * durationSeconds).coerceIn(12, 120)

            MediaCodecVideoEncoder.encodeToMp4(
                MediaCodecVideoEncoder.EncodeRequest(
                    context = context,
                    outputFile = outFile,
                    requestedMime = MediaFormat.MIMETYPE_VIDEO_AVC,
                    requestedWidth = alignedW,
                    requestedHeight = alignedH,
                    fps = fps,
                    bitrateBps = 2_500_000,
                    totalFrames = totalFrames,
                    includeAudio = false,
                    frameProvider = { frameIdx, targetW, targetH ->
                        renderTestSceneFrame(
                            width = targetW,
                            height = targetH,
                            frameIndex = frameIdx,
                            totalFrames = totalFrames,
                            random = Random(42L + frameIdx)
                        )
                    }
                )
            )

            Uri.fromFile(outFile)
        }
    }

    private fun renderTestSceneFrame(
        width: Int,
        height: Int,
        frameIndex: Int,
        totalFrames: Int,
        random: Random
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val progress = frameIndex.toFloat() / totalFrames.coerceAtLeast(1).toFloat()

        // Simulated camera jitter for testing Video Stabilization
        val jitterX = (sin(frameIndex * 1.7) * 5.0).toFloat()
        val jitterY = (cos(frameIndex * 2.3) * 4.0).toFloat()
        canvas.save()
        canvas.translate(jitterX, jitterY)

        // Twilight gradient sky background (slightly underexposed so Auto Color & Exposure shine)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(
                    Color.rgb(18, 24, 48),
                    Color.rgb(44, 30, 76),
                    Color.rgb(22, 48, 72)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Moving horizon grid & glowing neon sun/orb for Optical Flow / RIFE interpolation & 4K Upscaling
        val orbX = width * (0.2f + 0.6f * progress)
        val orbY = height * (0.42f - 0.12f * sin(progress * Math.PI).toFloat())
        val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(139, 92, 246)
        }
        canvas.drawCircle(orbX, orbY, height * 0.13f, orbPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 189, 248)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(orbX, orbY, height * 0.17f, ringPaint)

        // Color & Skin-Tone Reference Patches along bottom for Auto Color testing
        val patchColors = intArrayOf(
            Color.rgb(198, 144, 118), // Warm skin-tone reference
            Color.rgb(220, 68, 68),   // Cinema Red
            Color.rgb(52, 199, 123),  // Foliage Green
            Color.rgb(59, 130, 246),  // Sapphire Blue
            Color.rgb(234, 179, 8)    // Golden Highlight
        )
        val patchW = width / 7f
        val patchH = height * 0.12f
        val patchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (i in patchColors.indices) {
            patchPaint.color = patchColors[i]
            val left = width * 0.12f + i * (patchW + 10f)
            canvas.drawRoundRect(
                RectF(left, height * 0.78f, left + patchW, height * 0.78f + patchH),
                8f, 8f, patchPaint
            )
        }

        // Fine resolution test bars & text for Sharpening & Super Resolution verification
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = height * 0.055f
            isFakeBoldText = true
        }
        canvas.drawText("VIDEOX AI • TEST STREAM F#${frameIndex + 1}", width * 0.08f, height * 0.14f, textPaint)

        // Subtle sensor noise speckle in top-right quadrant for AI Denoiser testing
        val noisePaint = Paint()
        for (n in 0 until 120) {
            val nx = random.nextInt(width / 2, width - 4).toFloat()
            val ny = random.nextInt(4, height / 2).toFloat()
            val luma = random.nextInt(90, 210)
            noisePaint.color = Color.argb(95, luma, luma, luma)
            canvas.drawPoint(nx, ny, noisePaint)
        }

        canvas.restore()
        return bmp
    }

    companion object {
        fun bitmapToNv12(bitmap: Bitmap, width: Int, height: Int): ByteArray {
            val argb = IntArray(width * height)
            bitmap.getPixels(argb, 0, width, 0, 0, width, height)
            val yuv = ByteArray(width * height * 3 / 2)
            var yIndex = 0
            var uvIndex = width * height

            for (j in 0 until height) {
                for (i in 0 until width) {
                    val px = argb[j * width + i]
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF

                    val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                    yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                    if (j % 2 == 0 && i % 2 == 0 && uvIndex + 1 < yuv.size) {
                        val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                        val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                        yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                        yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
            return yuv
        }
    }
}
