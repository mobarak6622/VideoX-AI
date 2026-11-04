package com.example.feature.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.common.BilingualStrings
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess

data class VideoTemplateItem(
    val id: String,
    val title: String,
    val category: String,
    val durationLabel: String,
    val ratioLabel: String,
    val musicName: String,
    val gradientColors: List<Color>,
    val tags: List<String>
)

@Composable
fun TemplatesScreen(
    strings: BilingualStrings,
    onUseTemplate: (VideoTemplateItem) -> Unit
) {
    val categories = listOf(
        "Trending", "Cinematic", "TikTok", "Instagram Reels", "YouTube Intro",
        "Travel", "Wedding", "Birthday", "Business Ad", "Product Promotion",
        "Fashion", "Educational", "Sports"
    )

    var selectedCategory by remember { mutableStateOf("Trending") }
    var searchQuery by remember { mutableStateOf("") }

    val allTemplates = remember {
        listOf(
            VideoTemplateItem(
                id = "tmpl_1",
                title = "Cinematic Vlog Intro",
                category = "Cinematic",
                durationLabel = "12s",
                ratioLabel = "16:9",
                musicName = "Ambient Chill Lo-Fi",
                gradientColors = listOf(Color(0xFF2C3E50), Color(0xFF000000)),
                tags = listOf("Teal & Orange", "Slow Zoom", "Letterbox")
            ),
            VideoTemplateItem(
                id = "tmpl_2",
                title = "Fast Tempo Reels Montage",
                category = "Trending",
                durationLabel = "15s",
                ratioLabel = "9:16",
                musicName = "Cyberpunk Beat 135BPM",
                gradientColors = listOf(Color(0xFF4A00E0), Color(0xFF8E2DE2)),
                tags = listOf("Auto Beat Sync", "Glitch Flash", "Smooth 60FPS")
            ),
            VideoTemplateItem(
                id = "tmpl_3",
                title = "Luxury Fashion Reel",
                category = "Fashion",
                durationLabel = "18s",
                ratioLabel = "9:16",
                musicName = "Deep House Sunset",
                gradientColors = listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)),
                tags = listOf("Glow Effect", "Grain", "Vogue Titles")
            ),
            VideoTemplateItem(
                id = "tmpl_4",
                title = "Travel Memories Diary",
                category = "Travel",
                durationLabel = "30s",
                ratioLabel = "9:16",
                musicName = "Acoustic Horizon",
                gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
                tags = listOf("Speed Ramp", "Ken Burns", "Golden Hour")
            ),
            VideoTemplateItem(
                id = "tmpl_5",
                title = "Wedding Romance Cinematic",
                category = "Wedding",
                durationLabel = "45s",
                ratioLabel = "16:9",
                musicName = "Piano Elegance 432Hz",
                gradientColors = listOf(Color(0xFFBA8B02), Color(0xFF181818)),
                tags = listOf("DSLR Bokeh", "Film Warmth", "Subtitles")
            ),
            VideoTemplateItem(
                id = "tmpl_6",
                title = "Product Promo Commercial",
                category = "Product Promotion",
                durationLabel = "20s",
                ratioLabel = "9:16",
                musicName = "Upbeat Tech Groove",
                gradientColors = listOf(Color(0xFF00C9FF), Color(0xFF92FE9D)),
                tags = listOf("3D Motion", "Clean Cut", "Call to Action")
            ),
            VideoTemplateItem(
                id = "tmpl_7",
                title = "YouTube Gaming Intro",
                category = "YouTube Intro",
                durationLabel = "10s",
                ratioLabel = "16:9",
                musicName = "Heavy Bass Synth",
                gradientColors = listOf(Color(0xFFED213A), Color(0xFF93291E)),
                tags = listOf("RGB Split", "Shake", "Neon 3D")
            ),
            VideoTemplateItem(
                id = "tmpl_8",
                title = "Birthday Celebration Story",
                category = "Birthday",
                durationLabel = "15s",
                ratioLabel = "9:16",
                musicName = "Joyful Acoustic Guitar",
                gradientColors = listOf(Color(0xFFF857A6), Color(0xFFFF5858)),
                tags = listOf("Confetti FX", "Bounce Anim", "Pop Titles")
            )
        )
    }

    val filteredTemplates = allTemplates.filter { tmpl ->
        (selectedCategory == "Trending" || tmpl.category == selectedCategory) &&
            (searchQuery.isBlank() || tmpl.title.contains(searchQuery, ignoreCase = true) || tmpl.category.contains(searchQuery, ignoreCase = true))
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("templates_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EditX Viral Templates",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "One-tap CapCut Pro style animations, music, and transitions",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search templates (Reels, Cinematic, Travel...)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Category Filter Chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricPurple.copy(alpha = 0.35f),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        // Template Cards
        items(filteredTemplates) { item ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUseTemplate(item) }
                    .border(1.dp, Color(0xFF262A3B), RoundedCornerShape(20.dp))
            ) {
                Column {
                    // Preview Banner with Play overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Brush.horizontalGradient(item.gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Preview", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }

                        // Badges
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.65f)) {
                                Text(item.category, fontSize = 10.sp, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = ElectricPurple) {
                                Text("${item.ratioLabel} • ${item.durationLabel}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }

                    // Metadata & Use button
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(item.musicName, fontSize = 11.sp, color = Color.Gray)
                                }
                            }

                            Button(
                                onClick = { onUseTemplate(item) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Use", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item.tags.forEach { tag ->
                                Surface(shape = RoundedCornerShape(4.dp), color = Color.White.copy(alpha = 0.08f)) {
                                    Text("#$tag", fontSize = 10.sp, color = CyberBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
