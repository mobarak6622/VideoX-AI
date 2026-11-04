package com.example.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.ColorConfig
import com.example.core.common.ColorPreset
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess

enum class ActiveEditorTool(val title: String) {
    BASIC_EDIT("Basic Edit & Transform"),
    SPEED_STUDIO("Speed Control & Curves"),
    COLOR_STUDIO("14-Axis Color & LUT Grading"),
    EFFECTS_LIBRARY("Effects & Filters"),
    TRANSITIONS_ANIM("Transitions & Keyframes"),
    TEXT_BENGALI("Text & Bengali Typography"),
    AUDIO_STUDIO("Professional Audio Studio"),
    MASKING_CHROMA("Masking & Chroma Key"),
    AI_STUDIO("EditX AI Neural Studio")
}

/**
 * 1. Speed Control Studio (Normal, Slow-Mo, Fast, Curve Speed)
 */
@Composable
fun SpeedControlStudioPanel(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onApplySpeedCurve: (String) -> Unit
) {
    var speedVal by remember { mutableFloatStateOf(currentSpeed) }
    var selectedCurve by remember { mutableStateOf("Normal") }
    var smoothSlowMoEnabled by remember { mutableStateOf(true) }

    val curvePresets = listOf("Normal", "Montage", "Hero Cut", "Bullet Time", "Jump Cut", "Flash In")

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Speed Adjustment Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${String.format("%.1f", speedVal)}x", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ElectricPurple)
        }

        // Standard speed slider (0.1x to 10.0x)
        Slider(
            value = speedVal,
            onValueChange = {
                speedVal = it
                onSpeedChange(it)
            },
            valueRange = 0.1f..8.0f,
            colors = SliderDefaults.colors(thumbColor = ElectricPurple, activeTrackColor = ElectricPurple)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.2f, 0.5f, 1.0f, 1.5f, 2.0f, 4.0f).forEach { s ->
                FilterChip(
                    selected = kotlin.math.abs(speedVal - s) < 0.05f,
                    onClick = {
                        speedVal = s
                        onSpeedChange(s)
                    },
                    label = { Text("${s}x", fontSize = 11.sp) }
                )
            }
        }

        // Speed Curves Row (Montage, Hero Cut, Bullet Time)
        Text("CapCut Pro Speed Curves", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(curvePresets) { curve ->
                FilterChip(
                    selected = selectedCurve == curve,
                    onClick = {
                        selectedCurve = curve
                        onApplySpeedCurve(curve)
                    },
                    label = { Text(curve, fontSize = 12.sp) }
                )
            }
        }

        // Smooth Slow Motion Toggle
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Optical Flow Smooth Slow Motion", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Uses AI intermediate sub-frame synthesis for buttery smooth 120fps motion", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Switch(checked = smoothSlowMoEnabled, onCheckedChange = { smoothSlowMoEnabled = it })
        }
    }
}

/**
 * 2. Color Grading Studio (14-axis + LUT Presets + Curves)
 */
@Composable
fun ColorGradingStudioPanel(
    config: ColorConfig,
    onUpdateConfig: (ColorConfig) -> Unit,
    onOneTapAutoColor: () -> Unit
) {
    var exposure by remember { mutableFloatStateOf(config.exposure) }
    var contrast by remember { mutableFloatStateOf(config.contrast) }
    var saturation by remember { mutableFloatStateOf(config.saturation) }
    var vibrance by remember { mutableFloatStateOf(config.vibrance) }
    var temperature by remember { mutableFloatStateOf(config.temperature) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("14-Axis Color Grading Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Button(
                onClick = onOneTapAutoColor,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Auto Color", fontSize = 12.sp)
            }
        }

        // Cinematic LUT Presets
        Text("Cinematic 3D LUT Presets", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ColorPreset.entries) { preset ->
                FilterChip(
                    selected = config.preset == preset,
                    onClick = {
                        onUpdateConfig(config.copy(preset = preset, enabled = true))
                    },
                    label = { Text(preset.displayName, fontSize = 11.sp) }
                )
            }
        }

        // Sliders
        ColorSliderRow("Exposure", exposure, -1.0f..1.0f) {
            exposure = it
            onUpdateConfig(config.copy(exposure = it, enabled = true))
        }
        ColorSliderRow("Contrast", contrast, -0.8f..1.0f) {
            contrast = it
            onUpdateConfig(config.copy(contrast = it, enabled = true))
        }
        ColorSliderRow("Saturation", saturation, -1.0f..1.5f) {
            saturation = it
            onUpdateConfig(config.copy(saturation = it, enabled = true))
        }
        ColorSliderRow("Vibrance (Skin-Safe)", vibrance, -0.5f..1.2f) {
            vibrance = it
            onUpdateConfig(config.copy(vibrance = it, enabled = true))
        }
        ColorSliderRow("Color Temp (Warm/Cool)", temperature, -0.8f..0.8f) {
            temperature = it
            onUpdateConfig(config.copy(temperature = it, enabled = true))
        }
    }
}

@Composable
private fun ColorSliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            Text(String.format("%+.2f", value), style = MaterialTheme.typography.labelSmall, color = CyberBlue)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = CyberBlue, activeTrackColor = CyberBlue)
        )
    }
}

/**
 * 3. Text & Bengali Typography Studio (Bangla Unicode + Font Selection + Auto Captions)
 */
@Composable
fun TextAndBengaliStudioPanel(
    onAddTextClip: (text: String, font: String, isBengali: Boolean) -> Unit,
    onGenerateAutoCaptions: () -> Unit
) {
    var textInput by remember { mutableStateOf("সম্পাদনা করুন EditX AI") }
    var selectedFont by remember { mutableStateOf("Hind Siliguri") }
    val fonts = listOf("Hind Siliguri (বাংলা)", "Noto Sans Bengali", "Roboto Bold", "Cinematic Serif", "Cyber Sans")

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Text, Typography & Bengali Subtitles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("Enter Text / বাংলা লিখুন") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false
        )

        Text("Select Typography / ফন্ট নির্বাচন করুন", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(fonts) { font ->
                FilterChip(
                    selected = selectedFont == font,
                    onClick = { selectedFont = font },
                    label = { Text(font, fontSize = 12.sp) }
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val isBengali = textInput.any { it.code in 0x0980..0x09FF }
                    onAddTextClip(textInput, selectedFont, isBengali)
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
            ) {
                Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Text Layer")
            }

            OutlinedButton(
                onClick = onGenerateAutoCaptions,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Auto Captions")
            }
        }
    }
}

/**
 * 4. Audio Studio (Music, SFX, Voiceover Recorder, Ducking, Noise Reduction)
 */
@Composable
fun AudioStudioPanel(
    onAddMusic: (String) -> Unit,
    onAddSfx: (String) -> Unit,
    onStartVoiceover: () -> Unit
) {
    val sampleMusic = listOf("Cyberpunk Drive", "Cinematic Ambient", "Acoustic Warmth", "Lofi Sunset", "Action Beat")
    val sampleSfx = listOf("Whoosh Transition", "Cinematic Boom", "Camera Shutter", "Keyboard Click", "Pop Notification")

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Professional Audio Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Text("Royalty-Free BGM Library", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sampleMusic) { music ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = EmeraldSuccess.copy(alpha = 0.18f),
                    modifier = Modifier.clickable { onAddMusic(music) }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(music, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        Text("Sound Effects (SFX)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sampleSfx) { sfx ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CyberBlue.copy(alpha = 0.18f),
                    modifier = Modifier.clickable { onAddSfx(sfx) }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = CyberBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(sfx, fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        Button(
            onClick = onStartVoiceover,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Record Live Studio Voiceover Track")
        }
    }
}
