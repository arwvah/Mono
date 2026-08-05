package com.mono.fitness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mono.fitness.data.ActivityType
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.ui.components.PillButton
import com.mono.fitness.ui.components.PremiumStubBanner
import com.mono.fitness.ui.components.TypeChip
import com.mono.fitness.ui.theme.FrostedPanel
import com.mono.fitness.ui.theme.MonoScaffoldBackground
import com.mono.fitness.ui.theme.PillShape
import com.mono.fitness.util.Formatters

@Composable
fun RoutesScreen(
    repo: MonoRepository,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit
) {
    var filterType by remember { mutableStateOf<ActivityType?>(null) }
    var minKm by remember { mutableStateOf<Double?>(null) }
    var maxKm by remember { mutableStateOf<Double?>(null) }

    val routes by repo.observeRoutes(
        type = filterType?.name,
        minDistance = minKm?.times(1000.0),
        maxDistance = maxKm?.times(1000.0)
    ).collectAsState(initial = emptyList())

    MonoScaffoldBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Routes",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Light
                        )
                        Text(
                            "Plan & browse",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                    PillButton("Plan", onClick = onCreate)
                }
            }
            item {
                Text("Filter type", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterPill("All", filterType == null) { filterType = null }
                    ActivityType.entries.forEach { t ->
                        TypeChip(t, selected = filterType == t) {
                            filterType = if (filterType == t) null else t
                        }
                    }
                }
            }
            item {
                Text("Min distance", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterPill("Any min", minKm == null) { minKm = null }
                    FilterPill("≥ 5 km", minKm == 5.0) { minKm = 5.0 }
                    FilterPill("≥ 10 km", minKm == 10.0) { minKm = 10.0 }
                }
            }
            item {
                Text("Max distance", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterPill("Any max", maxKm == null) { maxKm = null }
                    FilterPill("≤ 5 km", maxKm == 5.0) { maxKm = 5.0 }
                    FilterPill("≤ 10 km", maxKm == 10.0) { maxKm = 10.0 }
                    FilterPill("≤ 20 km", maxKm == 20.0) { maxKm = 20.0 }
                }
            }
            item {
                PremiumStubBanner("Turn-by-turn navigation — Premium, not in v1")
            }
            if (routes.isEmpty()) {
                item {
                    Text(
                        "No saved routes. Tap Plan and tap the map to add points.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            } else {
                items(routes, key = { it.id }) { route ->
                    FrostedPanel(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(route.id) }
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                route.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "${ActivityType.fromName(route.activityType).label} · " +
                                    "${Formatters.distanceKm(route.distanceMeters)} · " +
                                    "${Formatters.elevation(route.elevationGainMeters, unknownWhenZero = true)} elev",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    Text(
        label,
        modifier = Modifier
            .clip(PillShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = fg,
        style = MaterialTheme.typography.labelLarge
    )
}
