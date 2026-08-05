package com.mono.fitness.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mono.fitness.data.ActivityType
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.ui.components.ActivityListCard
import com.mono.fitness.ui.components.PillButton
import com.mono.fitness.ui.components.TypeChip
import com.mono.fitness.ui.theme.MonoScaffoldBackground
import com.mono.fitness.util.Formatters
import com.mono.fitness.util.Gpx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    repo: MonoRepository,
    onOpenActivity: (Long) -> Unit,
    onAddManual: () -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    val activities by repo.observeActivities().collectAsState(initial = emptyList())
    var filterType by remember { mutableStateOf<ActivityType?>(null) }
    val filtered = remember(activities, filterType) {
        if (filterType == null) activities
        else activities.filter { it.type.equals(filterType!!.name, ignoreCase = true) }
    }
    val weekFrom = System.currentTimeMillis() - 7L * 86_400_000L
    val weekActs = activities.filter { it.startTimeMillis >= weekFrom }
    val weekDist = weekActs.sumOf { it.distanceMeters }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val gpxPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val imported = Gpx.import(stream)
                        if (imported.points.isEmpty()) {
                            throw IllegalArgumentException("No track points in file")
                        }
                        repo.importGpxTrack(imported.name, imported.type, imported.points)
                    } ?: throw IllegalStateException("Could not open file")
                }
                Toast.makeText(context, "GPX imported", Toast.LENGTH_SHORT).show()
                onOpenActivity(id)
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Import failed: ${e.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    MonoScaffoldBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Mono",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Activity Feed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings, 
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "THIS WEEK",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            Formatters.distanceKm(weekDist),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(text = "Add", onClick = onAddManual, filled = false)
                        PillButton(
                            text = "Import",
                            onClick = {
                                gpxPicker.launch(
                                    arrayOf(
                                        "application/gpx+xml",
                                        "application/octet-stream",
                                        "text/xml",
                                        "*/*"
                                    )
                                )
                            },
                            filled = false
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActivityType.entries.forEach { t ->
                        TypeChip(t, selected = filterType == t) {
                            filterType = if (filterType == t) null else t
                        }
                    }
                }
            }
            
            if (filtered.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (activities.isEmpty()) "No activities yet" else "No matching activities",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { act ->
                    ActivityListCard(act, onClick = { onOpenActivity(act.id) })
                }
            }
        }
    }
}
