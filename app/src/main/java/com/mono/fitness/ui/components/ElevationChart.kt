package com.mono.fitness.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mono.fitness.data.ActivityPoint
import com.mono.fitness.ui.theme.FrostedPanel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp

/**
 * Lightweight elevation profile (Canvas). Avoids hard dependency on Vico alpha API churn
 * while still providing a clean B/W chart. Dashboard uses the same style.
 */
@Composable
fun ElevationProfileChart(
    points: List<ActivityPoint>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
    title: String = "Elevation"
) {
    val elev = points.mapNotNull { it.elevationMeters }
    FrostedPanel(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (elev.size < 2) {
                Text(
                    "No elevation data",
                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            } else {
                val lineColor = MaterialTheme.colorScheme.onSurface
                val fillColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .padding(top = 12.dp)
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
                    // min/max ticks
                    drawLine(
                        lineColor.copy(alpha = 0.15f),
                        Offset(0f, 0f),
                        Offset(size.width, 0f),
                        strokeWidth = 1f
                    )
                }
                Text(
                    "${elev.min().toInt()} – ${elev.max().toInt()} m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SimpleBarChart(
    values: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    title: String,
    valueFormatter: (Double) -> String = { String.format("%.1f", it) }
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
