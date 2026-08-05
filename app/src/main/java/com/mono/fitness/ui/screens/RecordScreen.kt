package com.mono.fitness.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityType
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.tracking.TrackingController
import com.mono.fitness.ui.components.PillButton
import com.mono.fitness.ui.components.RouteMap
import com.mono.fitness.ui.components.StatBubble
import com.mono.fitness.ui.components.TypeChip
import com.mono.fitness.ui.theme.FrostedPanel
import com.mono.fitness.ui.theme.MonoScaffoldBackground
import com.mono.fitness.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun RecordScreen(
    repo: MonoRepository,
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by TrackingController.state.collectAsState()
    val running by TrackingController.running.collectAsState()
    val finished by TrackingController.finished.collectAsState()
    var selectedType by remember { mutableStateOf(ActivityType.RUN) }
    var hasLocationPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = fine || coarse

        if (!fine) {
            Toast.makeText(context, "Location permission required to record", Toast.LENGTH_LONG).show()
        } else {
            maybePromptBattery(context)
            TrackingController.start(context, selectedType)
        }
    }

    LaunchedEffect(finished) {
        val fin = finished ?: return@LaunchedEffect
        if (fin.points.isEmpty() && fin.distanceMeters < 5) {
            TrackingController.clearFinished()
            return@LaunchedEffect
        }
        scope.launch {
            val type = ActivityType.fromName(fin.type)
            val cal = MonoRepository.estimateCalories(type, fin.distanceMeters, fin.movingTimeMillis)
            val avg = if (fin.movingTimeMillis > 0)
                fin.distanceMeters / (fin.movingTimeMillis / 1000.0) else 0.0
            val id = repo.saveActivity(
                Activity(
                    type = fin.type,
                    title = "${type.label} · ${Formatters.date(fin.startTimeMillis)}",
                    distanceMeters = fin.distanceMeters,
                    durationMillis = fin.durationMillis,
                    movingTimeMillis = fin.movingTimeMillis,
                    avgSpeedMps = avg,
                    maxSpeedMps = fin.maxSpeedMps,
                    elevationGainMeters = fin.elevationGainMeters,
                    calories = cal,
                    avgHeartRate = fin.avgHeartRate,
                    maxHeartRate = fin.maxHeartRate,
                    startTimeMillis = fin.startTimeMillis,
                    endTimeMillis = fin.endTimeMillis,
                    isManual = false,
                    source = "gps"
                ),
                fin.points
            )
            TrackingController.clearFinished()
            onSaved(id)
        }
    }

    val points = snapshot.points.map { LatLng(it.latitude, it.longitude) }

    MonoScaffoldBackground {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                RouteMap(
                    points = points,
                    modifier = Modifier.fillMaxSize(),
                    followLast = running && !snapshot.paused,
                    showMyLocation = hasLocationPermission
                )
                
                // HUD
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    FrostedPanel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp)) {
                            Text(
                                if (!running) "READY"
                                else if (snapshot.paused) "PAUSED"
                                else "RECORDING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                Formatters.distanceKm(
                                    if (running) snapshot.distanceMeters else 0.0
                                ),
                                style = MaterialTheme.typography.displayLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatBubble(
                                    "Time",
                                    Formatters.duration(if (running) snapshot.durationMillis else 0),
                                    Modifier.weight(1f)
                                )
                                StatBubble(
                                    "Pace",
                                    Formatters.paceFromActivity(
                                        snapshot.distanceMeters,
                                        snapshot.movingTimeMillis
                                    ),
                                    Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatBubble(
                                    "Elev",
                                    Formatters.elevation(
                                        if (running) snapshot.elevationGainMeters else 0.0
                                    ),
                                    Modifier.weight(1f)
                                )
                                StatBubble(
                                    "HR",
                                    Formatters.heartRate(
                                        if (running) snapshot.currentHeartRate else null
                                    ),
                                    modifier = Modifier.weight(1f),
                                    badge = if (running) {
                                        val conn by TrackingController.hrConnected.collectAsState(initial = false)
                                        if (conn) "LIVE" else null
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            FrostedPanel(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 80.dp)
            ) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!running) {
                        Text(
                            "ACTIVITY TYPE", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(
                                ActivityType.RUN, ActivityType.RIDE, ActivityType.WALK,
                                ActivityType.HIKE, ActivityType.SWIM, ActivityType.GYM
                            ).forEach { t ->
                                TypeChip(t, selected = t == selectedType) { selectedType = t }
                            }
                        }
                        PillButton(
                            text = "Start Session",
                            onClick = {
                                val perms = mutableListOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms += Manifest.permission.POST_NOTIFICATIONS
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    perms += Manifest.permission.BLUETOOTH_SCAN
                                    perms += Manifest.permission.BLUETOOTH_CONNECT
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            PillButton(
                                text = if (snapshot.paused) "Resume" else "Pause",
                                onClick = {
                                    if (snapshot.paused) TrackingController.resume(context)
                                    else TrackingController.pause(context)
                                },
                                modifier = Modifier.weight(1f),
                                filled = false
                            )
                            PillButton(
                                text = "Finish",
                                onClick = { TrackingController.stop(context) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun maybePromptBattery(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val pm = context.getSystemService(PowerManager::class.java) ?: return
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
        }
    }
}
