package com.example.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.processing.AiVideoQueueTask
import com.example.core.processing.QueueTaskStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberBlue
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.EmeraldSuccess

@Composable
fun QueueManagerScreen(
    tasks: List<AiVideoQueueTask>,
    onCancelTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    onClearCompleted: () -> Unit,
    onOpenVideoPreview: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("queue_manager_screen"),
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
                        Icon(Icons.Default.Layers, contentDescription = null, tint = ElectricPurple, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Video Processing Queue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "WorkManager Background Queue • Preprocessing & Cloud AI Pipelines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (tasks.any { it.status == QueueTaskStatus.COMPLETED || it.status == QueueTaskStatus.FAILED || it.status == QueueTaskStatus.CANCELLED }) {
                    IconButton(onClick = onClearCompleted) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Completed", tint = AmberWarning)
                    }
                }
            }
        }

        if (tasks.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = ElectricPurple.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Text("No Tasks in Queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Select a video from the Editor or Preview screen and tap 'Add to Background AI Processing Queue'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                QueueTaskCard(
                    task = task,
                    onCancel = { onCancelTask(task.id) },
                    onRemove = { onRemoveTask(task.id) },
                    onPreview = {
                        val uriToPreview = task.outputUriString ?: task.sourceUriString
                        onOpenVideoPreview(uriToPreview)
                    }
                )
            }
        }
    }
}

@Composable
private fun QueueTaskCard(
    task: AiVideoQueueTask,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit
) {
    val (statusColor, statusBg) = when (task.status) {
        QueueTaskStatus.COMPLETED -> EmeraldSuccess to EmeraldSuccess.copy(alpha = 0.15f)
        QueueTaskStatus.ENHANCING, QueueTaskStatus.ENCODING, QueueTaskStatus.CLOUD_AI_ANALYZING -> ElectricPurple to ElectricPurple.copy(alpha = 0.15f)
        QueueTaskStatus.PREPROCESSING, QueueTaskStatus.QUEUED -> CyberBlue to CyberBlue.copy(alpha = 0.15f)
        QueueTaskStatus.FAILED, QueueTaskStatus.CANCELLED -> CrimsonError to CrimsonError.copy(alpha = 0.15f)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .testTag("task_card_${task.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBg
                ) {
                    Text(
                        text = task.status.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (task.status == QueueTaskStatus.ENHANCING ||
                task.status == QueueTaskStatus.ENCODING ||
                task.status == QueueTaskStatus.PREPROCESSING ||
                task.status == QueueTaskStatus.CLOUD_AI_ANALYZING
            ) {
                LinearProgressIndicator(
                    progress = { (task.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = ElectricPurple
                )
            }

            Text(
                text = task.stageMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${task.pipeline.upscale.targetResolution.shortBadge} • ${task.pipeline.bokeh.aperture}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberBlue
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (task.status == QueueTaskStatus.COMPLETED) {
                        Button(
                            onClick = onPreview,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ExoPlayer Preview", fontSize = 12.sp)
                        }
                    }

                    if (task.status == QueueTaskStatus.ENHANCING ||
                        task.status == QueueTaskStatus.PREPROCESSING ||
                        task.status == QueueTaskStatus.QUEUED
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Cancel", fontSize = 12.sp, color = CrimsonError)
                        }
                    } else {
                        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Cancel, contentDescription = "Remove", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
