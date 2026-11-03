package com.example.feature.upscaler

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.DslrBokehConfig
import com.example.feature.importer.BeforeAfterFrameCanvas
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DslrBokehScreen(
    config: DslrBokehConfig,
    originalFrame: Bitmap?,
    enhancedFrame: Bitmap?,
    onUpdate: (DslrBokehConfig) -> Unit,
    onApplyAiDirector: () -> Unit
) {
    val apertures = listOf("f/1.2", "f/1.4", "f/1.8", "f/2.8", "f/4.0")
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dslr_bokeh_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CapCut Pro DSLR Camera & Bokeh", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "Cinematic Depth of Field & Lens Blur (Full-Frame 35mm/85mm Bokeh)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = config.enabled,
                    onCheckedChange = { onUpdate(config.copy(enabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ElectricPurple),
                    modifier = Modifier.testTag("switch_dslr_bokeh")
                )
            }
        }

        // Before/After Interactive Preview Canvas
        if (originalFrame != null && enhancedFrame != null) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, ElectricPurple.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                ) {
                    BeforeAfterFrameCanvas(
                        originalFrame = originalFrame,
                        enhancedFrame = enhancedFrame,
                        splitFraction = splitFraction,
                        onSplitChanged = { splitFraction = it },
                        beforeLabel = "Original",
                        afterLabel = "DSLR ${config.aperture} Bokeh"
                    )
                }
            }
        }

        // 1. Lens Aperture Selection
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Lens Aperture (Depth of Field)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        apertures.forEach { ap ->
                            FilterChip(
                                selected = config.aperture == ap,
                                onClick = { onUpdate(config.copy(aperture = ap, enabled = true)) },
                                label = { Text(ap, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricPurple.copy(alpha = 0.35f),
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // 2. Bokeh Blur & Falloff Controls
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Bokeh Blur & Depth Tuning", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    // Blur Radius
                    Text("Background Blur Intensity: ${(config.blurRadius * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = config.blurRadius,
                        onValueChange = { onUpdate(config.copy(blurRadius = it, enabled = true)) },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = ElectricPurple, activeTrackColor = ElectricPurple)
                    )

                    // Specular Highlight Boost
                    Text("Creamy Bokeh Specular Highlights: ${(config.bokehHighlightBoost * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = config.bokehHighlightBoost,
                        onValueChange = { onUpdate(config.copy(bokehHighlightBoost = it, enabled = true)) },
                        valueRange = 0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = CyberBlue, activeTrackColor = CyberBlue)
                    )

                    // Portrait Isolation Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Portrait Subject Lock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Keeps face and subject pin-sharp while isolating background", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = config.portraitIsolation,
                            onCheckedChange = { onUpdate(config.copy(portraitIsolation = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ElectricPurple)
                        )
                    }
                }
            }
        }

        // 3. AI Gemini Director One-Tap Auto Button
        item {
            Button(
                onClick = onApplyAiDirector,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_ai_director_dslr"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Gemini AI Director: Auto DSLR Cinema Grade", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
