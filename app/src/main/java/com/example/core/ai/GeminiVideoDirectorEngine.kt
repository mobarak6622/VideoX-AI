package com.example.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.core.common.ColorConfig
import com.example.core.common.ColorPreset
import com.example.core.common.DslrBokehConfig
import com.example.core.common.SharpenConfig
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.TextPart
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiDirector"

data class CapCutProAiEnhancementPlan(
    val genreTitle: String,
    val directorAdviceBengali: String,
    val recommendedColor: ColorConfig,
    val recommendedBokeh: DslrBokehConfig,
    val recommendedSharpen: SharpenConfig,
    val isCloudAiSuccess: Boolean
)

class GeminiVideoDirectorEngine(private val context: Context) {

    // Mandated by firebase-ai-logic-android: Lazy initialization with supported model gemini-3.8-flash
    private val generativeModel by lazy {
        val directorSchema = Schema.obj(
            properties = mapOf(
                "genreTitle" to Schema.string("Cinematic genre or lighting style (e.g. Cinematic Teal & Gold, DSLR Golden Portrait)"),
                "directorAdviceBengali" to Schema.string("1-2 sentences of professional advice in Bengali explaining why this grading was applied"),
                "exposure" to Schema.double("Exposure adjustment from -0.3 to +0.3"),
                "contrast" to Schema.double("Contrast adjustment from -0.2 to +0.4"),
                "saturation" to Schema.double("Saturation adjustment from -0.2 to +0.4"),
                "temperature" to Schema.double("Color temperature from -0.4 (cool) to +0.4 (warm)"),
                "tint" to Schema.double("Tint from -0.2 (green) to +0.2 (magenta)"),
                "highlights" to Schema.double("Highlights compression from -0.4 to +0.2"),
                "shadows" to Schema.double("Shadows lift from -0.2 to +0.4"),
                "vibrance" to Schema.double("Vibrance boost from 0.0 to 0.4"),
                "recommendedAperture" to Schema.string("DSLR lens aperture: f/1.2, f/1.4, f/1.8, or f/2.8"),
                "bokehBlurRadius" to Schema.double("Bokeh blur radius from 0.2 to 0.8"),
                "sharpnessStrength" to Schema.double("Detail enhancement sharpness from 0.2 to 0.6")
            )
        )

        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = directorSchema
        }

        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    suspend fun analyzeFrameAndGenerateProPlan(
        keyframeBitmap: Bitmap,
        videoResolution: String
    ): CapCutProAiEnhancementPlan {
        return try {
            // Downscale frame to ~480px for swift <2MB payload delivery
            val scaled = if (keyframeBitmap.width > 512 || keyframeBitmap.height > 512) {
                val scale = 512f / maxOf(keyframeBitmap.width, keyframeBitmap.height)
                Bitmap.createScaledBitmap(
                    keyframeBitmap,
                    (keyframeBitmap.width * scale).toInt().coerceAtLeast(16),
                    (keyframeBitmap.height * scale).toInt().coerceAtLeast(16),
                    true
                )
            } else {
                keyframeBitmap
            }

            val promptText = "You are a world-class Hollywood Film Colorist and AI Video Director like CapCut Pro Master. " +
                "Analyze this video frame ($videoResolution). " +
                "Evaluate the lighting, dynamic range, skin tones, subject focus, and ambient contrast. " +
                "Output optimal cinematic 14-axis color grading, DSLR camera depth/bokeh aperture, and edge detail parameters to give this video a professional DSLR cinema look."

            val contentPrompt = Content(
                parts = listOf(
                    ImagePart(scaled),
                    TextPart(promptText)
                )
            )

            val response = withTimeout(45_000L) {
                generativeModel.generateContent(contentPrompt)
            }

            if (scaled !== keyframeBitmap) {
                scaled.recycle()
            }

            val jsonText = response.text
            if (jsonText.isNullOrBlank()) {
                fallbackLocalProPlan("Gemini returned empty payload")
            } else {
                parseDirectorJson(jsonText)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini Director analysis timed out", e)
            fallbackLocalProPlan("Gemini AI বিশ্লেষণ টাইমআউট হয়েছে (অফলাইন প্রো গ্রেডিং সক্রিয় করা হয়েছে)।")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Director call failed, using graceful local Pro profile", e)
            fallbackLocalProPlan("ইন্টারনেট সংযোগ বা অ্যাপ চেক অনুপলব্ধ থাকায় CapCut Pro অফলাইন প্রোফাইল সক্রিয় করা হয়েছে: ${e.localizedMessage}")
        }
    }

    private fun parseDirectorJson(jsonStr: String): CapCutProAiEnhancementPlan {
        val root = JSONObject(jsonStr)
        val genre = root.optString("genreTitle", "Cinematic Studio Pro")
        val adviceBn = root.optString(
            "directorAdviceBengali",
            "ভিডিওর আলো ও রঙের সঠিক ভারসাম্য বজায় রেখে ডিএসএলআর ক্যামেরার মতো ব্যাকগ্রাউন্ড ব্লার ও সিনেম্যাটিক টোন যুক্ত করা হয়েছে।"
        )
        val exp = root.optDouble("exposure", 0.08).toFloat().coerceIn(-0.3f, 0.3f)
        val cont = root.optDouble("contrast", 0.16).toFloat().coerceIn(-0.2f, 0.4f)
        val sat = root.optDouble("saturation", 0.14).toFloat().coerceIn(-0.2f, 0.4f)
        val temp = root.optDouble("temperature", 0.04).toFloat().coerceIn(-0.4f, 0.4f)
        val tint = root.optDouble("tint", 0.02).toFloat().coerceIn(-0.2f, 0.2f)
        val hl = root.optDouble("highlights", -0.12).toFloat().coerceIn(-0.4f, 0.2f)
        val sh = root.optDouble("shadows", 0.12).toFloat().coerceIn(-0.2f, 0.4f)
        val vib = root.optDouble("vibrance", 0.18).toFloat().coerceIn(0f, 0.5f)
        val aperture = root.optString("recommendedAperture", "f/1.8")
        val bokehRadius = root.optDouble("bokehBlurRadius", 0.55).toFloat().coerceIn(0.1f, 1f)
        val sharp = root.optDouble("sharpnessStrength", 0.38).toFloat().coerceIn(0.1f, 0.8f)

        return CapCutProAiEnhancementPlan(
            genreTitle = genre,
            directorAdviceBengali = adviceBn,
            recommendedColor = ColorConfig(
                enabled = true,
                autoColor = false,
                preset = ColorPreset.CINEMATIC,
                exposure = exp,
                contrast = cont,
                saturation = sat,
                temperature = temp,
                tint = tint,
                highlights = hl,
                shadows = sh,
                vibrance = vib
            ),
            recommendedBokeh = DslrBokehConfig(
                enabled = true,
                aperture = aperture,
                blurRadius = bokehRadius,
                focalDistance = 0.45f,
                depthFalloff = 0.55f,
                bokehHighlightBoost = 0.4f,
                portraitIsolation = true
            ),
            recommendedSharpen = SharpenConfig(
                strength = sharp,
                edgeEnhancement = sharp * 0.85f,
                clarity = 0.25f,
                fineDetailRecovery = 0.35f,
                textureRecovery = 0.30f,
                deblurring = true
            ),
            isCloudAiSuccess = true
        )
    }

    private fun fallbackLocalProPlan(reason: String): CapCutProAiEnhancementPlan {
        return CapCutProAiEnhancementPlan(
            genreTitle = "CapCut Pro Studio HDR (DSLR Mode)",
            directorAdviceBengali = "ডিএসএলআর ফোকাস ও ব্যাকগ্রাউন্ড বোকেহসহ সিনেম্যাটিক গোল্ডেন কালার ব্যালেন্স প্রয়োগ করা হয়েছে। ($reason)",
            recommendedColor = ColorConfig(
                enabled = true,
                autoColor = true,
                preset = ColorPreset.CINEMATIC,
                exposure = 0.08f,
                contrast = 0.18f,
                saturation = 0.15f,
                temperature = 0.05f,
                tint = 0.02f,
                highlights = -0.15f,
                shadows = 0.14f,
                vibrance = 0.22f
            ),
            recommendedBokeh = DslrBokehConfig(
                enabled = true,
                aperture = "f/1.8",
                blurRadius = 0.55f,
                focalDistance = 0.45f,
                depthFalloff = 0.55f,
                bokehHighlightBoost = 0.45f,
                portraitIsolation = true
            ),
            recommendedSharpen = SharpenConfig(
                strength = 0.42f,
                edgeEnhancement = 0.35f,
                clarity = 0.28f,
                fineDetailRecovery = 0.35f,
                textureRecovery = 0.30f,
                deblurring = true
            ),
            isCloudAiSuccess = false
        )
    }
}
