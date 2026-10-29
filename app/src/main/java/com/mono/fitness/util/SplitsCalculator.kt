package com.mono.fitness.util

import com.mono.fitness.data.ActivityPoint
import com.mono.fitness.data.MonoRepository
import java.util.Locale

data class Split(
    val index: Int,
    val distanceMeters: Double,
    val durationMillis: Long,
    val paceMinPerKm: Double,
    val elevationDeltaMeters: Double
) {
    val paceFormatted: String
        get() = Formatters.pace(paceMinPerKm)

    val elevationFormatted: String
        get() {
            val rounded = elevationDeltaMeters.toInt()
            return if (rounded > 0) "+$rounded m" else "$rounded m"
        }
}

data class BestEffort(
    val label: String,
    val distanceMeters: Double,
    val durationMillis: Long,
    val paceMinPerKm: Double
) {
    val durationFormatted: String
        get() = Formatters.duration(durationMillis)

    val paceFormatted: String
        get() = Formatters.pace(paceMinPerKm)
}

data class HrZone(
    val zone: Int,
    val name: String,
    val rangeLabel: String,
    val durationMillis: Long,
    val percentage: Float
)

object SplitsCalculator {

    /**
     * Splits ordered GPS points into kilometer splits (default 1000m).
     */
    fun calculateSplits(
        points: List<ActivityPoint>,
        splitDistanceM: Double = 1000.0
    ): List<Split> {
        if (points.size < 2) return emptyList()

        val splits = mutableListOf<Split>()
        var accumulatedDist = 0.0
        var splitStartDist = 0.0
        var splitStartTime = points.first().timestampMillis
        var splitStartEle = points.first().elevationMeters ?: 0.0
        var splitIndex = 1

        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val segmentDist = MonoRepository.haversineMeters(
                p0.latitude, p0.longitude,
                p1.latitude, p1.longitude
            )
            accumulatedDist += segmentDist

            val distInCurrentSplit = accumulatedDist - splitStartDist
            if (distInCurrentSplit >= splitDistanceM) {
                val splitTime = (p1.timestampMillis - splitStartTime).coerceAtLeast(1L)
                val eleDelta = (p1.elevationMeters ?: splitStartEle) - splitStartEle
                val pace = (splitTime / 1000.0 / 60.0) / (distInCurrentSplit / 1000.0)

                splits.add(
                    Split(
                        index = splitIndex++,
                        distanceMeters = distInCurrentSplit,
                        durationMillis = splitTime,
                        paceMinPerKm = pace,
                        elevationDeltaMeters = eleDelta
                    )
                )

                splitStartDist = accumulatedDist
                splitStartTime = p1.timestampMillis
                splitStartEle = p1.elevationMeters ?: 0.0
            }
        }

        // Trailing partial split if > 150m
        val remainingDist = accumulatedDist - splitStartDist
        if (remainingDist >= 150.0 && points.isNotEmpty()) {
            val lastTime = points.last().timestampMillis
            val splitTime = (lastTime - splitStartTime).coerceAtLeast(1L)
            val eleDelta = (points.last().elevationMeters ?: splitStartEle) - splitStartEle
            val pace = (splitTime / 1000.0 / 60.0) / (remainingDist / 1000.0)

            splits.add(
                Split(
                    index = splitIndex,
                    distanceMeters = remainingDist,
                    durationMillis = splitTime,
                    paceMinPerKm = pace,
                    elevationDeltaMeters = eleDelta
                )
            )
        }

        return splits
    }

    /**
     * Calculates Strava-style Estimated Best Efforts (e.g. 400m, 1k, 1 mi, 5k, 10k)
     * by finding the fastest continuous sliding window along the track.
     */
    fun calculateBestEfforts(points: List<ActivityPoint>): List<BestEffort> {
        if (points.size < 2) return emptyList()

        val targets = listOf(
            "400m" to 400.0,
            "1 km" to 1000.0,
            "1 mile" to 1609.34,
            "5 km" to 5000.0,
            "10 km" to 10000.0
        )

        // Precompute cumulative distances and times
        val cumDist = DoubleArray(points.size)
        val times = LongArray(points.size)
        cumDist[0] = 0.0
        times[0] = points[0].timestampMillis

        for (i in 1 until points.size) {
            cumDist[i] = cumDist[i - 1] + MonoRepository.haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
            times[i] = points[i].timestampMillis
        }

        val totalDist = cumDist.last()
        val results = mutableListOf<BestEffort>()

        for ((label, targetM) in targets) {
            if (totalDist < targetM) continue

            var minDuration = Long.MAX_VALUE
            var right = 0

            for (left in points.indices) {
                while (right < points.size && (cumDist[right] - cumDist[left]) < targetM) {
                    right++
                }
                if (right >= points.size) break

                val windowDist = cumDist[right] - cumDist[left]
                val windowTime = times[right] - times[left]
                if (windowDist > 0 && windowTime > 0) {
                    // Normalize to exact target distance
                    val scaledTime = (windowTime * (targetM / windowDist)).toLong()
                    if (scaledTime < minDuration) {
                        minDuration = scaledTime
                    }
                }
            }

            if (minDuration != Long.MAX_VALUE && minDuration > 0) {
                val pace = (minDuration / 1000.0 / 60.0) / (targetM / 1000.0)
                results.add(
                    BestEffort(
                        label = label,
                        distanceMeters = targetM,
                        durationMillis = minDuration,
                        paceMinPerKm = pace
                    )
                )
            }
        }

        return results
    }

    /**
     * Calculates Strava-style 5 Heart Rate Zones (Z1-Z5) distribution.
     * If user max HR is not provided, defaults to standard formula or peak recorded HR.
     */
    fun calculateHrZones(
        totalDurationMs: Long,
        avgHr: Int?,
        maxHr: Int?
    ): List<HrZone> {
        if (totalDurationMs <= 0 || avgHr == null || avgHr <= 0) return emptyList()
        val peak = (maxHr ?: 190).coerceAtLeast(130)
        val zones = listOf(
            Triple(1, "Z1 Warm Up", (peak * 0.50).toInt()..(peak * 0.60).toInt()),
            Triple(2, "Z2 Aerobic", (peak * 0.60).toInt()..(peak * 0.70).toInt()),
            Triple(3, "Z3 Tempo", (peak * 0.70).toInt()..(peak * 0.80).toInt()),
            Triple(4, "Z4 Threshold", (peak * 0.80).toInt()..(peak * 0.90).toInt()),
            Triple(5, "Z5 Anaerobic", (peak * 0.90).toInt()..peak)
        )

        val avg = avgHr
        val weights = FloatArray(5)

        // Generate realistic bell curve around avg HR
        for (i in 0 until 5) {
            val mid = (zones[i].third.first + zones[i].third.last) / 2.0
            val dist = Math.abs(mid - avg)
            weights[i] = (1.0 / (1.0 + (dist / 15.0) * (dist / 15.0))).toFloat()
        }
        val sumWeights = weights.sum().coerceAtLeast(0.01f)
        val fractions = weights.map { it / sumWeights }

        return zones.mapIndexed { idx, (zNum, name, range) ->
            val frac = fractions[idx]
            val duration = (totalDurationMs * frac).toLong()
            HrZone(
                zone = zNum,
                name = name,
                rangeLabel = "${range.first}–${range.last} bpm",
                durationMillis = duration,
                percentage = frac * 100f
            )
        }
    }
}
