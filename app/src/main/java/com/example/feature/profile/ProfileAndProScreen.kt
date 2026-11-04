package com.example.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

@Composable
fun ProfileAndProScreen(
    strings: BilingualStrings,
    projectCount: Int,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrivacy: () -> Unit
) {
    var isSubscribedPro by remember { mutableStateOf(true) }
    var selectedPlan by remember { mutableStateOf("Yearly") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .testTag("profile_and_pro_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // User Profile Header Card
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF262A3B), RoundedCornerShape(22.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(ElectricPurple, CyberBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White, modifier = Modifier.size(34.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("EditX Creator", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(shape = RoundedCornerShape(6.dp), color = ElectricPurple) {
                                Text("PRO VIP", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Active Projects: $projectCount • On-Device Engine", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        // EditX AI PRO VIP Membership Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141224)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Brush.horizontalGradient(listOf(ElectricPurple, CyberBlue)), RoundedCornerShape(24.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EditX AI Pro VIP", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        Surface(shape = RoundedCornerShape(50), color = EmeraldSuccess.copy(alpha = 0.2f)) {
                            Text("ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }

                    Text(
                        "Unlock genuine on-device 4K 60fps export, neural network AI Super Resolution, 120fps frame interpolation, and all CapCut Pro cinematic tools.",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )

                    // Feature highlights list
                    val proPerks = listOf(
                        "4K UHD (2160p) Hardware MediaCodec Video Encoding",
                        "AI Super Resolution (Local ONNX Neural Upscaler)",
                        "12 Multi-Layer Tracks & Subtitle Synchronizer",
                        "Bengali & English Typography with Custom Fonts",
                        "Zero Watermark & Uncapped Bitrate Archival Export"
                    )

                    proPerks.forEach { perk ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(perk, fontSize = 12.sp, color = Color.White)
                        }
                    }

                    // Plan selection
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedPlan == "Yearly") ElectricPurple.copy(alpha = 0.3f) else Color(0xFF1D202E)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPlan = "Yearly" }
                                .border(1.dp, if (selectedPlan == "Yearly") ElectricPurple else Color.Transparent, RoundedCornerShape(14.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Surface(shape = RoundedCornerShape(4.dp), color = EmeraldSuccess) {
                                    Text("SAVE 58%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Yearly VIP", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("$39.99 / year", fontSize = 12.sp, color = CyberBlue)
                            }
                        }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedPlan == "Monthly") ElectricPurple.copy(alpha = 0.3f) else Color(0xFF1D202E)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPlan = "Monthly" }
                                .border(1.dp, if (selectedPlan == "Monthly") ElectricPurple else Color.Transparent, RoundedCornerShape(14.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Monthly", fontWeight = FontWeight.Bold, color = Color.White)
                                Text("$7.99 / month", fontSize = 12.sp, color = CyberBlue)
                                Text("Cancel anytime", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }

                    Button(
                        onClick = { isSubscribedPro = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Pro Subscription (Active)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Account & Security Links
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Account & System Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open EditX App Settings & Language (English / বাংলা)")
                    }

                    OutlinedButton(
                        onClick = onNavigateToPrivacy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Local Privacy Architecture")
                    }
                }
            }
        }
    }
}
