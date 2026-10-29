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
import com.mono.fitness.data.ActivityType
import com.mono.fitness.tracking.TrackingController
import com.mono.fitness.ui.components.PillButton
import com.mono.fitness.ui.components.RouteMap
import com.mono.fitness.ui.components.StatBubble
import com.mono.fitness.ui.components.TypeChip
import com.mono.fitness.ui.theme.FrostedPanel
import com.mono.fitness.ui.theme.MonoScaffoldBackground
import com.mono.fitness.util.Formatters

@Composable
fun RecordScreen(
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val snapshot by TrackingController.state.collectAsState()
    val running by TrackingController.running.collectAsState()
    val finished by TrackingController.finished.collectAsState()
    var selectedType by remember { mutableStateOf(ActivityType.RUN) }
    var finishRequested by remember { mutableStateOf(false) }
    var startRequested by remember { mutableStateOf(false) }
    val saveError by TrackingController.saveError.collectAsState()

    // Reset local finish lock when a new session starts.
    LaunchedEffect(running) {
        if (running) {
            finishRequested = false
            startRequested = false
        }
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    LaunchedEffect(saveError) {
        if (saveError != null) finishRequested = false
    }

    val startWorkout = {
        if (!startRequested && !running) {
            startRequested = true
            try {
                maybePromptBattery(context)
                TrackingController.start(context, selectedType)
            } catch (_: Exception) {
                startRequested = false
                Toast.makeText(context, "Could not start recording. Check location permission.", Toast.LENGTH_LONG).show()
            }
        }
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
            startWorkout()
        }
    }

    // The service owns persistence; the screen only opens the committed activity.
    LaunchedEffect(finished) {
        val fin = TrackingController.consumeFinished() ?: return@LaunchedEffect
        finishRequested = false
        onSaved(fin.activityId)
    }

    val points = snapshot.points.map { LatLng(it.latitude, it.longitude) }
    // Prefer live snapshot over the running flag so a brief service rebind
    // after pause/resume does not flash zeros on the HUD.
    val sessionLive = snapshot.recording

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
                    followLast = snapshot.recording && !snapshot.paused,
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
                                if (!snapshot.recording) "READY"
                                else if (snapshot.paused) "PAUSED"
                                else "RECORDING",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                Formatters.distanceKm(
                                    if (sessionLive) snapshot.distanceMeters else 0.0
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
                                    Formatters.duration(if (sessionLive) snapshot.durationMillis else 0),
                                    Modifier.weight(1f)
                                )
                                StatBubble(
                                    "Pace",
                                    Formatters.paceFromActivity(
                                        if (sessionLive) snapshot.distanceMeters else 0.0,
                                        if (sessionLive) snapshot.movingTimeMillis else 0L
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
                                        if (sessionLive) snapshot.elevationGainMeters else 0.0
                                    ),
                                    Modifier.weight(1f)
                                )
                                StatBubble(
                                    "HR",
                                    Formatters.heartRate(
                                        if (sessionLive) snapshot.currentHeartRate else null
                                    ),
                                    modifier = Modifier.weight(1f),
                                    badge = if (sessionLive) {
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
                    if (saveError != null) {
                        Text(saveError!!, color = MaterialTheme.colorScheme.error)
                    }
                    if (!sessionLive) {
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
                            text = if (startRequested) "Starting…" else "Start Session",
                            enabled = !startRequested,
                            onClick = {
                                val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (fineGranted) {
                                    startWorkout()
                                } else {
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
                                }
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
                                enabled = !snapshot.saving,
                                onClick = {
                                    if (snapshot.paused) TrackingController.resume(context)
                                    else TrackingController.pause(context)
                                },
                                modifier = Modifier.weight(1f),
                                filled = false
                            )
                            PillButton(
                                text = if (finishRequested || snapshot.saving) "Saving…" else "Finish",
                                enabled = !finishRequested && !snapshot.saving,
                                onClick = {
                                    if (finishRequested) return@PillButton
                                    finishRequested = true
                                    TrackingController.stop(context)
                                },
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
