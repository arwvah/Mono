package com.mono.fitness.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mono.fitness.data.ActivityPoint
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.ui.theme.FrostedPanel
import com.mono.fitness.ui.theme.PillShape
import com.mono.fitness.util.Formatters
import java.util.Locale

/**
 * Strava-style Workout Profile Chart supporting toggle between Elevation and Pace curves.
 */
@Composable
fun WorkoutProfileChart(
    points: List<ActivityPoint>,
    isCycling: Boolean = false,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Elevation, 1 = Pace/Speed
    val elev = remember(points) { points.mapNotNull { it.elevationMeters } }

    val speeds = remember(points) {
        if (points.size < 2) emptyList()
        else {
            val list = mutableListOf<Double>()
            for (i in 1 until points.size) {
                val p0 = points[i - 1]
                val p1 = points[i]
                val d = MonoRepository.haversineMeters(p0.latitude, p0.longitude, p1.latitude, p1.longitude)
                val dt = (p1.timestampMillis - p0.timestampMillis) / 1000.0
                val speedKmh = if (dt > 0.5) (d / dt) * 3.6 else (p1.speedMps ?: 0.0) * 3.6
                if (speedKmh in 0.5..80.0) {
                    list.add(speedKmh)
                }
            }
            list
        }
    }

    FrostedPanel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (selectedTab == 0) "ELEVATION PROFILE" else if (isCycling) "SPEED PROFILE" else "PACE PROFILE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                // Segmented Pill Toggle
                Row(
                    Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(2.dp)
                ) {
                    ChartTabPill(
                        label = "Elev",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 }
                    )
                    ChartTabPill(
                        label = if (isCycling) "Speed" else "Pace",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (selectedTab == 0) {
                // Elevation Chart
                if (elev.size < 2) {
                    Text(
                        "No elevation data available",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                } else {
                    val lineColor = MaterialTheme.colorScheme.onSurface
                    val fillColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(height)
                    ) {
                        val minE = elev.min()
                        val maxE = elev.max().coerceAtLeast(minE + 1.0)
                        val path = Path()
                        val fill = Path()
                        elev.forEachIndexed { i, e ->
                            val x = size.width * i / (elev.size - 1).toFloat()
                            val y = size.height * (1f - ((e - minE) / (maxE - minE)).toFloat())
                            if (i == 0) {
                                path.moveTo(x, y)
                                fill.moveTo(x, size.height)
                                fill.lineTo(x, y)
                            } else {
                                path.lineTo(x, y)
                                fill.lineTo(x, y)
                            }
                        }
                        fill.lineTo(size.width, size.height)
                        fill.close()
                        drawPath(fill, fillColor)
                        drawPath(
                            path,
                            lineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Min ${elev.min().toInt()} m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            "Max ${elev.max().toInt()} m",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                // Pace / Speed Chart
                if (speeds.size < 2) {
                    Text(
                        "No speed data recorded",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                } else {
                    val lineColor = MaterialTheme.colorScheme.primary
                    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(height)
                    ) {
                        val minS = speeds.min()
                        val maxS = speeds.max().coerceAtLeast(minS + 0.1)
                        val path = Path()
                        val fill = Path()
                        speeds.forEachIndexed { i, s ->
                            val x = size.width * i / (speeds.size - 1).toFloat()
                            val y = size.height * (1f - ((s - minS) / (maxS - minS)).toFloat())
                            if (i == 0) {
                                path.moveTo(x, y)
                                fill.moveTo(x, size.height)
                                fill.lineTo(x, y)
                            } else {
                                path.lineTo(x, y)
                                fill.lineTo(x, y)
                            }
                        }
                        fill.lineTo(size.width, size.height)
                        fill.close()
                        drawPath(fill, fillColor)
                        drawPath(
                            path,
                            lineColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (isCycling) String.format(Locale.US, "Min %.1f km/h", speeds.min())
                            else "Avg ${String.format(Locale.US, "%.1f km/h", speeds.average())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            String.format(Locale.US, "Peak %.1f km/h", speeds.max()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = fg,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ElevationProfileChart(
    points: List<ActivityPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    title: String = "Elevation"
) {
    WorkoutProfileChart(points = points, isCycling = false, modifier = modifier, height = height)
}

@Composable
fun SimpleBarChart(
    values: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    title: String,
    valueFormatter: (Double) -> String = { String.format(Locale.US, "%.1f", it) }
) {
    FrostedPanel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (values.isEmpty()) {
                Text(
                    "No data",
                    modifier = Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            } else {
                val maxV = values.maxOf { it.second }.coerceAtLeast(1.0)
                val barColor = MaterialTheme.colorScheme.onSurface
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .padding(top = 16.dp)
                ) {
                    val n = values.size
                    val gap = size.width * 0.04f
                    val barW = (size.width - gap * (n + 1)) / n
                    values.forEachIndexed { i, (_, v) ->
                        val h = (v / maxV * size.height * 0.85).toFloat()
                        val left = gap + i * (barW + gap)
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.85f),
                            topLeft = Offset(left, size.height - h),
                            size = androidx.compose.ui.geometry.Size(barW, h),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f)
                        )
                    }
                }
                RowLabels(values.map { it.first })
                Text(
                    "Peak ${valueFormatter(maxV)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RowLabels(labels: List<String>) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
    ) {
        labels.forEach {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
