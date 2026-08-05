package com.mono.fitness.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

/** Premium Dark Monochrome map style */
private val MonoMapStyle = """
[
  {"elementType":"geometry","stylers":[{"color":"#121212"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#616161"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#121212"}]},
  {"featureType":"administrative","elementType":"geometry","stylers":[{"color":"#757575"}]},
  {"featureType":"poi","elementType":"labels.text.fill","stylers":[{"color":"#757575"}]},
  {"featureType":"poi.park","elementType":"geometry","stylers":[{"color":"#181818"}]},
  {"featureType":"road","elementType":"geometry.fill","stylers":[{"color":"#2c2c2c"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#8a8a8a"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#000000"}]}
]
""".trimIndent()

@Composable
fun RouteMap(
    points: List<LatLng>,
    modifier: Modifier = Modifier,
    followLast: Boolean = false,
    showMyLocation: Boolean = false,
    onMapClick: ((LatLng) -> Unit)? = null,
    extraMarkers: List<LatLng> = emptyList()
) {
    val context = LocalContext.current
    val hasPermission = remember(showMyLocation) {
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            points.lastOrNull() ?: LatLng(37.7749, -122.4194),
            14f
        )
    }

    LaunchedEffect(points.size, followLast) {
        when {
            followLast && points.isNotEmpty() -> {
                camera.animate(CameraUpdateFactory.newLatLng(points.last()))
            }
            points.size >= 2 -> {
                try {
                    val b = LatLngBounds.builder()
                    points.forEach { b.include(it) }
                    camera.animate(CameraUpdateFactory.newLatLngBounds(b.build(), 100))
                } catch (_: Exception) {
                }
            }
            points.size == 1 -> {
                camera.animate(CameraUpdateFactory.newLatLngZoom(points.first(), 15f))
            }
        }
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = camera,
        properties = MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = showMyLocation && hasPermission,
            mapStyleOptions = MapStyleOptions(MonoMapStyle)
        ),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = showMyLocation && hasPermission,
            compassEnabled = false,
            mapToolbarEnabled = false
        ),
        onMapClick = { latLng -> onMapClick?.invoke(latLng) }
    ) {
        if (points.size >= 2) {
            Polyline(
                points = points, 
                color = Color.White, 
                width = 8f,
                geodesic = true
            )
        }
        points.firstOrNull()?.let {
            Marker(state = MarkerState(it), title = "Start")
        }
        if (!followLast) {
            points.lastOrNull()?.takeIf { points.size > 1 }?.let {
                Marker(state = MarkerState(it), title = "End")
            }
        }
        extraMarkers.forEachIndexed { i, ll ->
            Marker(state = MarkerState(ll), title = "Point ${i + 1}")
        }
    }
}
