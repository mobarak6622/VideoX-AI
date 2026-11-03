package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.ai.LocalAiModelTask
import com.example.core.ai.OnnxAiEngine
import com.example.core.common.ColorConfig
import com.example.core.common.ColorPreset
import com.example.core.common.DenoiseConfig
import com.example.core.common.FilterStrength
import com.example.core.common.SharpenConfig
import com.example.core.common.StabilizeConfig
import com.example.core.gpu.DeviceCapabilityAnalyzer
import com.example.core.processing.ColorCorrectionProcessor
import com.example.core.processing.DenoiseAndSharpenProcessor
import com.example.core.processing.StabilizationAndFpsProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `manifest enforces strict offline privacy without INTERNET permission`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requestedPermissions = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse(
            "INTERNET permission must NOT be present in offline VideoX AI",
            requestedPermissions.contains(android.Manifest.permission.INTERNET)
        )
    }

    @Test
    fun `bundled local ONNX models extract and pass validation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = OnnxAiEngine(context)
        val models = engine.ensureBundledModelsExtracted()
        assertEquals(3, models.size)
        models.forEach { info ->
            assertTrue("Model ${info.task.title} should be extracted locally", info.isAvailable)
            assertTrue("Model file size must be > 64 bytes", info.fileSizeBytes > 64L)
        }
    }

    @Test
    fun `local DSP processors execute color grading, denoising, sharpening, stabilization and optical flow`() {
        val frameA = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(60, 80, 110))
        }
        val frameB = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(65, 85, 115))
        }

        // 1. Auto Color & 14-Axis Grading
        val autoEst = ColorCorrectionProcessor.analyzeFrameForAutoColor(frameA)
        val graded = ColorCorrectionProcessor.processFrame(
            frameA,
            ColorConfig(enabled = true, autoColor = true, preset = ColorPreset.CINEMATIC),
            autoEst
        )
        assertEquals(64, graded.width)
        assertEquals(64, graded.height)

        // 2. Spatial-Temporal Denoising
        val denoised = DenoiseAndSharpenProcessor.applyDenoise(
            currentFrame = graded,
            previousDenoisedFrame = frameA,
            config = DenoiseConfig(strength = FilterStrength.MEDIUM)
        )
        assertEquals(64, denoised.width)

        // 3. Halo-Suppressed Smart Sharpening
        val sharpened = DenoiseAndSharpenProcessor.applySharpenAndDetail(
            source = denoised,
            config = SharpenConfig(strength = 0.5f, fineDetailRecovery = 0.4f)
        )
        assertEquals(64, sharpened.width)

        // 4. Motion Estimation & Stabilization Warp
        val motion = StabilizationAndFpsProcessor.estimateMotionVector(frameA, frameB)
        assertNotNull(motion)
        val stabilized = StabilizationAndFpsProcessor.applyStabilizationWarp(
            frame = sharpened,
            smoothedCorrectionDx = -motion.dx,
            smoothedCorrectionDy = -motion.dy,
            config = StabilizeConfig(strength = FilterStrength.MEDIUM)
        )
        assertEquals(64, stabilized.width)

        // 5. Optical Flow Intermediate Frame Interpolation
        val interpolated = StabilizationAndFpsProcessor.interpolateIntermediateFrame(
            frameA = frameA,
            frameB = frameB,
            alpha = 0.5f,
            sceneCutDetection = true,
            useAiRifeModel = false,
            onnxEngine = null
        )
        assertEquals(64, interpolated.width)
    }

    @Test
    fun `device capability analyzer reports RAM, thermal state and codec matrix`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = DeviceCapabilityAnalyzer(context).analyze()
        assertTrue(report.totalRamMb > 0)
        assertTrue(report.cpuCores >= 1)
        assertNotNull(report.recommendedProfile)
    }

    @Test
    fun `home dashboard renders and navigates to AI upscaler, tools hub and settings`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_bar_app_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_import_photo_picker").assertIsDisplayed()

        // Navigate to Tools Hub tab
        composeTestRule.onNodeWithTag("nav_tab_tools").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tools_hub_screen").assertIsDisplayed()

        // Open AI Upscaler screen
        composeTestRule.onNodeWithTag("tool_card_upscaler").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_upscaler_screen").assertIsDisplayed()

        // Navigate to Settings tab
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
    }
}
