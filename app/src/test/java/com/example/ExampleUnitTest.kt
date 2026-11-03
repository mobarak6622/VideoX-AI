package com.example

import com.example.core.common.AppLanguage
import com.example.core.common.AppStrings
import com.example.core.common.FilterStrength
import com.example.core.common.StabilizeConfig
import com.example.core.common.TargetResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `target resolution preserves portrait and landscape aspect ratios with even dimensions`() {
        val (landW, landH) = TargetResolution.UHD_4K.computeOutputDimensions(1920, 1080)
        assertEquals(3840, landW)
        assertEquals(2160, landH)

        val (portW, portH) = TargetResolution.FHD_1080P.computeOutputDimensions(720, 1280)
        assertEquals(1080, portW)
        assertEquals(1920, portH)
        assertTrue(portW % 2 == 0 && portH % 2 == 0)
    }

    @Test
    fun `bilingual localization provides English and Bengali strings`() {
        val en = AppStrings.get(AppLanguage.ENGLISH)
        val bn = AppStrings.get(AppLanguage.BENGALI)
        assertEquals("VideoX AI", en.appName)
        assertEquals("VideoX AI", bn.appName)
        assertTrue(bn.tagline.contains("অফলাইন"))
    }

    @Test
    fun `stabilization framing loss scales with intensity`() {
        val offCfg = StabilizeConfig(strength = FilterStrength.OFF)
        val highCfg = StabilizeConfig(strength = FilterStrength.HIGH)
        assertEquals(0f, offCfg.estimatedFramingLossPercent, 0.01f)
        assertTrue(highCfg.estimatedFramingLossPercent > 10f)
    }
}
