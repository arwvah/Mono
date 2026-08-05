package com.mono.fitness.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.mono.fitness.data.ActivityType
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.ui.components.PremiumStubBanner
import com.mono.fitness.ui.components.RouteMap
import com.mono.fitness.ui.components.StatBubble
import com.mono.fitness.ui.theme.FrostedPanel
import com.mono.fitness.ui.theme.MonoScaffoldBackground
import com.mono.fitness.util.Formatters
import com.mono.fitness.util.Gpx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    repo: MonoRepository,
    routeId: Long,
    onBack: () -> Unit
) {
    val route by repo.observeRoute(routeId).collectAsState(initial = null)
    val points by repo.observeRoutePoints(routeId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val r = route ?: return
    val latLngs = points.map { LatLng(it.latitude, it.longitude) }
    var confirmDelete by remember { mutableStateOf(false) }
    val hasElev = points.any { it.elevationMeters != null } || r.elevationGainMeters > 0

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete route?") },
            text = { Text("“${r.name}” will be removed permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repo.deleteRoute(r.id)
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
                title = { Text(r.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val dir = File(context.cacheDir, "gpx").also { it.mkdirs() }
                                    val f = File(dir, "route_${r.id}.gpx")
                                    f.outputStream().use { out ->
                                        Gpx.exportRoute(
                                            name = r.name,
                                            type = r.activityType,
                                            points = points.map { it.latitude to it.longitude },
                                            elevations = points.map { it.elevationMeters },
                                            out = out
                                        )
                                    }
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".fileprovider",
                                        f
                                    )
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_SEND
                                    ).apply {
                                        type = "application/gpx+xml"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        android.content.Intent.createChooser(intent, "Export route GPX")
                                    )
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "Export failed: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.FileUpload, "Export")
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
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    FrostedPanel(Modifier.fillMaxSize()) {
                        RouteMap(points = latLngs, modifier = Modifier.fillMaxSize())
                    }
                }
                
                Column {
                    Text(
                        "${ActivityType.fromName(r.activityType).label} · ${Formatters.distanceKm(r.distanceMeters)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Created ${Formatters.date(r.createdAtMillis)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatBubble(
                        "Distance",
                        Formatters.distanceKm(r.distanceMeters),
                        Modifier.weight(1f)
                    )
                    StatBubble(
                        "Elev",
                        Formatters.elevation(r.elevationGainMeters, unknownWhenZero = !hasElev),
                        Modifier.weight(1f)
                    )
                }
                
                StatBubble(
                    "Waypoints",
                    "${points.size} points",
                    Modifier.fillMaxWidth()
                )
                
                PremiumStubBanner("Navigate Route — Premium")
                
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}
