package com.mono.fitness.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.mono.fitness.data.ActivityType
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.ui.components.ElevationProfileChart
import com.mono.fitness.ui.components.PillButton
import com.mono.fitness.ui.components.RouteMap
import com.mono.fitness.ui.components.StatBubble
import com.mono.fitness.ui.theme.FrostedPanel
import com.mono.fitness.ui.theme.MonoScaffoldBackground
import com.mono.fitness.util.Formatters
import com.mono.fitness.util.ShareCardExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    repo: MonoRepository,
    activityId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val activity by repo.observeActivity(activityId).collectAsState(initial = null)
    val points by repo.observePoints(activityId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val act = activity ?: return
    var confirmDelete by remember { mutableStateOf(false) }

    val activityType = ActivityType.fromName(act.type)
    val latLngs = points.map { LatLng(it.latitude, it.longitude) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete activity?") },
            text = { Text("This removes the activity and its GPS track. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repo.deleteActivity(act.id)
                        onBack()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    MonoScaffoldBackground {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { 
                    Text(
                        act.title.ifBlank { activityType.label },
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val bmp = withContext(Dispatchers.Default) {
                                ShareCardExporter.render(act, points)
                            }
                            ShareCardExporter.saveToGallery(
                                context, bmp, "mono_${act.id}.png"
                            )
                            ShareCardExporter.share(context, bmp, "mono_${act.id}.png")
                        }
                    }) {
                        Icon(Icons.Outlined.IosShare, "Share")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, "Edit")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Outlined.Delete, "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column {
                    Text(
                        Formatters.dateTime(act.startTimeMillis),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        activityType.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                if (latLngs.isNotEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        FrostedPanel(Modifier.fillMaxSize()) {
                            RouteMap(
                                points = latLngs,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatBubble("Distance", Formatters.distanceKm(act.distanceMeters), Modifier.weight(1f))
                    StatBubble(
                        "Pace",
                        Formatters.paceFromActivity(act.distanceMeters, act.movingTimeMillis),
                        Modifier.weight(1f)
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatBubble("Time", Formatters.duration(act.durationMillis), Modifier.weight(1f))
                    StatBubble("Elevation", Formatters.elevation(act.elevationGainMeters), Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatBubble("Calories", "${act.calories} kcal", Modifier.weight(1f))
                    StatBubble("Avg HR", Formatters.heartRate(act.avgHeartRate), Modifier.weight(1f))
                }

                if (points.any { it.elevationMeters != null }) {
                    ElevationProfileChart(points)
                }

                if (act.notes.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "NOTES", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        FrostedPanel(Modifier.fillMaxWidth()) {
                            Text(
                                act.notes, 
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                PillButton(
                    text = "Share Activity Card",
                    onClick = {
                        scope.launch {
                            val bmp = withContext(Dispatchers.Default) {
                                ShareCardExporter.render(act, points)
                            }
                            ShareCardExporter.saveToGallery(context, bmp, "mono_${act.id}")
                            Toast.makeText(context, "Saved to gallery", Toast.LENGTH_SHORT).show()
                            ShareCardExporter.share(context, bmp)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}
