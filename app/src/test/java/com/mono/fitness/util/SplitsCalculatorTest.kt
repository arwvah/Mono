package com.mono.fitness.util

import com.mono.fitness.data.ActivityPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitsCalculatorTest {

    @Test
    fun calculateSplits_emptyOrSinglePoint_returnsEmpty() {
        assertTrue(SplitsCalculator.calculateSplits(emptyList()).isEmpty())
        val single = listOf(
            ActivityPoint(activityId = 1, latitude = 0.0, longitude = 0.0, timestampMillis = 0L, sequence = 0)
        )
        assertTrue(SplitsCalculator.calculateSplits(single).isEmpty())
    }

    @Test
    fun calculateSplits_multiKilometerRun_generatesSplits() {
        // Generate points spanning ~2.5 km
        val points = mutableListOf<ActivityPoint>()
        val startMs = 1_000_000L
        // ~111 km per degree lat -> 0.009 deg ≈ 1 km
        for (i in 0..25) {
            val lat = 37.7749 + i * 0.0009
            points.add(
                ActivityPoint(
                    activityId = 1,
                    latitude = lat,
                    longitude = -122.4194,
                    elevationMeters = 20.0 + i,
                    timestampMillis = startMs + i * 30_000L, // 30s per 100m = 5:00 /km
                    sequence = i
                )
            )
        }

        val splits = SplitsCalculator.calculateSplits(points)
        assertTrue("Expected at least 2 splits, got ${splits.size}", splits.size >= 2)
        assertEquals(1, splits[0].index)
        assertEquals(2, splits[1].index)
        assertTrue("Pace should be around 5 min/km, got ${splits[0].paceMinPerKm}", splits[0].paceMinPerKm in 4.5..5.5)
    }

    @Test
    fun calculateBestEfforts_findsBestSegments() {
        val points = mutableListOf<ActivityPoint>()
        val startMs = 1_000_000L
        for (i in 0..60) {
            val lat = 37.7749 + i * 0.0009
            points.add(
                ActivityPoint(
                    activityId = 1,
                    latitude = lat,
                    longitude = -122.4194,
                    timestampMillis = startMs + i * 25_000L,
                    sequence = i
                )
            )
        }

        val efforts = SplitsCalculator.calculateBestEfforts(points)
        assertFalse(efforts.isEmpty())
        val oneKm = efforts.find { it.label == "1 km" }
        assertTrue(oneKm != null)
        assertTrue(oneKm!!.durationMillis > 0)
    }

    @Test
    fun calculateHrZones_sumsToOneHundredPercent() {
        val zones = SplitsCalculator.calculateHrZones(
            totalDurationMs = 3600_000L,
            avgHr = 155,
            maxHr = 185
        )
        assertEquals(5, zones.size)
        val totalPct = zones.sumOf { it.percentage.toDouble() }
        assertEquals(100.0, totalPct, 0.5)
    }
}
