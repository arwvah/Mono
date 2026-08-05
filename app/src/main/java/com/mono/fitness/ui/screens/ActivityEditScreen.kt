package com.mono.fitness.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityType
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.ui.components.PillButton
import com.mono.fitness.ui.components.TypeChip
import com.mono.fitness.ui.theme.CardShape
import com.mono.fitness.ui.theme.MonoScaffoldBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityEditScreen(
    repo: MonoRepository,
    activityId: Long?,
    onDone: (Long?) -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(ActivityType.RUN) }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var distanceKm by remember { mutableStateOf("") }
    var durationMin by remember { mutableStateOf("") }
    var elev by remember { mutableStateOf("") }
    var calories by remember { mutableStateOf("") }
    var avgHr by remember { mutableStateOf("") }
    var maxHr by remember { mutableStateOf("") }
    var existing by remember { mutableStateOf<Activity?>(null) }

    LaunchedEffect(activityId) {
        if (activityId != null) {
            repo.getActivity(activityId)?.let { a ->
                existing = a
                type = ActivityType.fromName(a.type)
                title = a.title
                notes = a.notes
                distanceKm = (a.distanceMeters / 1000.0).let {
                    if (it == 0.0) "" else String.format("%.2f", it)
                }
                durationMin = (a.durationMillis / 60_000.0).let {
                    if (it == 0.0) "" else String.format("%.0f", it)
                }
                elev = if (a.elevationGainMeters == 0.0) "" else a.elevationGainMeters.toInt().toString()
                calories = if (a.calories == 0) "" else a.calories.toString()
                avgHr = a.avgHeartRate?.toString().orEmpty()
                maxHr = a.maxHeartRate?.toString().orEmpty()
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        cursorColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        focusedLabelColor = MaterialTheme.colorScheme.onSurface
    )

    MonoScaffoldBackground {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        if (activityId == null) "Add Activity" else "Edit Activity",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ACTIVITY TYPE", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ActivityType.entries.forEach { t ->
                            TypeChip(t, selected = t == type, onClick = { type = t })
                        }
                    }
                }
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CardShape,
                    colors = fieldColors,
                    singleLine = true
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = distanceKm,
                        onValueChange = { distanceKm = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Distance (km)") },
                        modifier = Modifier.weight(1f),
                        shape = CardShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = durationMin,
                        onValueChange = { durationMin = it.filter { c -> c.isDigit() } },
                        label = { Text("Time (min)") },
                        modifier = Modifier.weight(1f),
                        shape = CardShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = elev,
                        onValueChange = { elev = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Elevation (m)") },
                        modifier = Modifier.weight(1f),
                        shape = CardShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it.filter { c -> c.isDigit() } },
                        label = { Text("Calories") },
                        modifier = Modifier.weight(1f),
                        shape = CardShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = avgHr,
                        onValueChange = { avgHr = it.filter { c -> c.isDigit() } },
                        label = { Text("Avg HR") },
                        modifier = Modifier.weight(1f),
                        shape = CardShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxHr,
                        onValueChange = { maxHr = it.filter { c -> c.isDigit() } },
                        label = { Text("Max HR") },
                        modifier = Modifier.weight(1f),
                        shape = CardShape,
                        colors = fieldColors,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = CardShape,
                    colors = fieldColors
                )
                
                PillButton(
                    text = "Save Activity",
                    onClick = {
                        scope.launch {
                            val distM = (distanceKm.toDoubleOrNull() ?: 0.0) * 1000.0
                            val durMs = ((durationMin.toDoubleOrNull() ?: 0.0) * 60_000).toLong()
                            val elevM = elev.toDoubleOrNull() ?: 0.0
                            val cal = calories.toIntOrNull()
                                ?: MonoRepository.estimateCalories(type, distM, durMs)
                            val avgSpeed = if (durMs > 0) distM / (durMs / 1000.0) else 0.0
                            val base = existing
                            val now = System.currentTimeMillis()
                            val activity = Activity(
                                id = base?.id ?: 0,
                                type = type.name,
                                title = title.ifBlank { type.label },
                                notes = notes,
                                distanceMeters = distM,
                                durationMillis = durMs,
                                movingTimeMillis = base?.movingTimeMillis?.takeIf { it > 0 } ?: durMs,
                                avgSpeedMps = avgSpeed,
                                maxSpeedMps = base?.maxSpeedMps ?: avgSpeed,
                                elevationGainMeters = elevM,
                                calories = cal,
                                avgHeartRate = avgHr.toIntOrNull(),
                                maxHeartRate = maxHr.toIntOrNull(),
                                startTimeMillis = base?.startTimeMillis ?: now,
                                endTimeMillis = (base?.startTimeMillis ?: now) + durMs,
                                isManual = base?.isManual ?: true,
                                source = base?.source ?: "manual"
                            )
                            val id = repo.saveActivity(activity)
                            onDone(id)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
