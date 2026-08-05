package com.mono.fitness.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.mono.fitness.data.Route
import com.mono.fitness.data.RoutePoint
import com.mono.fitness.ui.components.PillButton
import com.mono.fitness.ui.components.RouteMap
import com.mono.fitness.ui.components.TypeChip
import com.mono.fitness.ui.theme.CardShape
import com.mono.fitness.ui.theme.FrostedPanel
import com.mono.fitness.ui.theme.MonoScaffoldBackground
import com.mono.fitness.util.ElevationApi
import com.mono.fitness.util.Formatters
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditScreen(
    repo: MonoRepository,
    onDone: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val points = remember { mutableStateListOf<LatLng>() }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ActivityType.RUN) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dist = MonoRepository.pathDistance(points.map { it.latitude to it.longitude })

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
                title = { Text("Plan Route", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            Box(Modifier.weight(1f)) {
                RouteMap(
                    points = points.toList(),
                    modifier = Modifier.fillMaxSize(),
                    extraMarkers = points.toList(),
                    onMapClick = { ll -> points.add(ll) }
                )
                FrostedPanel(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "TAP MAP TO ADD WAYPOINTS", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${points.size} points · ${Formatters.distanceKm(dist)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            FrostedPanel(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Route Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CardShape,
                        singleLine = true,
                        colors = fieldColors
                    )
                    
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            ActivityType.RUN, ActivityType.RIDE,
                            ActivityType.WALK, ActivityType.HIKE
                        ).forEach { t ->
                            TypeChip(t, selected = t == type) { type = t }
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton(
                            text = "Undo",
                            onClick = { if (points.isNotEmpty()) points.removeAt(points.lastIndex) },
                            enabled = points.isNotEmpty() && !saving,
                            filled = false,
                            modifier = Modifier.weight(1f)
                        )
                        PillButton(
                            text = if (saving) "Saving…" else "Save Route",
                            onClick = {
                                if (points.size < 2 || saving) return@PillButton
                                saving = true
                                scope.launch {
                                    try {
                                        val latLngPairs = points.map { it.latitude to it.longitude }
                                        val elevs = ElevationApi.fetchElevations(latLngPairs)
                                        val elevGain = MonoRepository.elevationGain(elevs)
                                        val id = repo.saveRoute(
                                            Route(
                                                name = name.ifBlank { "Route ${Formatters.distanceKm(dist)}" },
                                                activityType = type.name,
                                                distanceMeters = dist,
                                                elevationGainMeters = elevGain
                                            ),
                                            points.mapIndexed { i, ll ->
                                                RoutePoint(
                                                    routeId = 0,
                                                    latitude = ll.latitude,
                                                    longitude = ll.longitude,
                                                    elevationMeters = elevs.getOrNull(i),
                                                    sequence = i
                                                )
                                            }
                                        )
                                        onDone(id)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Save failed: ${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        saving = false
                                    }
                                }
                            },
                            enabled = points.size >= 2 && !saving,
                            modifier = Modifier.weight(2f)
                        )
                    }
                }
            }
        }
    }
}
