package com.mono.fitness.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mono.fitness.data.ActivityType
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.data.RecordKeys
import com.mono.fitness.ui.components.PremiumStubBanner
import com.mono.fitness.ui.components.SimpleBarChart
import com.mono.fitness.ui.components.StatBubble
import com.mono.fitness.ui.theme.FrostedPanel
import com.mono.fitness.ui.theme.MonoScaffoldBackground
import com.mono.fitness.util.Formatters
import java.util.Calendar
import java.util.Locale

@Composable
fun StatsScreen(repo: MonoRepository) {
    val activities by repo.observeActivities().collectAsState(initial = emptyList())
    val records by repo.observePersonalRecords().collectAsState(initial = emptyList())

    val now = System.currentTimeMillis()
    val weekFrom = now - 7L * 86_400_000L
    val monthFrom = now - 30L * 86_400_000L

    val week = activities.filter { it.startTimeMillis >= weekFrom }
    val month = activities.filter { it.startTimeMillis >= monthFrom }

    val weekDist = week.sumOf { it.distanceMeters }
    val weekTime = week.sumOf { it.durationMillis }
    val monthDist = month.sumOf { it.distanceMeters }
    val monthElev = month.sumOf { it.elevationGainMeters }

    val last7Bars = remember(activities) {
        (6 downTo 0).map { offset ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -offset)
            val label = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.US)
                ?: "?"
            val start = cal.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = start + 86_400_000L
            val d = activities
                .filter { it.startTimeMillis in start until end }
                .sumOf { it.distanceMeters } / 1000.0
            label to d
        }
    }

    MonoScaffoldBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Column(
                    Modifier
                        .padding(top = 48.dp, bottom = 8.dp)
                ) {
                    Text(
                        "Stats",
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Training Analytics",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
            
            item {
                Text(
                    "THIS WEEK",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatBubble("Distance", Formatters.distanceKm(weekDist), Modifier.weight(1f))
                    StatBubble("Time", Formatters.duration(weekTime), Modifier.weight(1f))
                }
            }
            
            item {
                SimpleBarChart(
                    values = last7Bars,
                    title = "Daily Distance (km)",
                    valueFormatter = { String.format(Locale.US, "%.1f km", it) }
                )
            }

            item {
                Text(
                    "LAST 30 DAYS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatBubble("Distance", Formatters.distanceKm(monthDist), Modifier.weight(1f))
                    StatBubble("Elevation", Formatters.elevation(monthElev), Modifier.weight(1f))
                }
            }

            item {
                Text(
                    "PERSONAL RECORDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
            
            if (records.isEmpty()) {
                item {
                    FrostedPanel(Modifier.fillMaxWidth()) {
                        Text(
                            "PRs appear automatically as you log activities.",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                items(records) { pr ->
                    FrostedPanel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "${ActivityType.fromName(pr.activityType).label} · ${pr.label}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                formatPr(pr.recordKey, pr.value, pr.unit),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                Formatters.date(pr.achievedAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
            item {
                PremiumStubBanner("Advanced Analytics — Premium")
            }
        }
    }
}

private fun formatPr(key: String, value: Double, unit: String): String = when (key) {
    RecordKeys.FASTEST_PACE -> Formatters.pace(value)
    RecordKeys.LONGEST_DISTANCE -> Formatters.distanceKm(value)
    RecordKeys.MOST_ELEVATION -> Formatters.elevation(value)
    RecordKeys.LONGEST_DURATION -> Formatters.duration(value.toLong())
    else -> String.format(Locale.US, "%.1f %s", value, unit)
}
