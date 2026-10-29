package com.mono.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest {

    @Test
    fun generateRunTrack_hasValidPointsAndSequences() {
        val start = 1_000_000L
        val dur = 26 * 60_000L
        val points = SeedData.generateRunTrack(start, dur)

        assertTrue(points.size >= 14)
        assertEquals(0, points.first().sequence)
        assertEquals(points.size - 1, points.last().sequence)
        assertEquals(start, points.first().timestampMillis)
        assertEquals(start + dur, points.last().timestampMillis)

        // Verify latitude and longitude bounds
        for (p in points) {
            assertTrue(p.latitude in 37.7..37.8)
            assertTrue(p.longitude in -122.5..-122.4)
            assertTrue((p.elevationMeters ?: 0.0) > 0.0)
        }
    }

    @Test
    fun generateRideTrack_hasElevationAndDistance() {
        val start = 1_000_000L
        val dur = 55 * 60_000L
        val points = SeedData.generateRideTrack(start, dur)

        assertTrue(points.size >= 15)
        val dist = MonoRepository.pathDistance(points.map { it.latitude to it.longitude })
        assertTrue("Ride distance should be significant: $dist", dist > 10_000.0)
    }

    @Test
    fun generateHikeTrack_hasSignificantClimb() {
        val start = 1_000_000L
        val dur = 100 * 60_000L
        val points = SeedData.generateHikeTrack(start, dur)

        assertTrue(points.size >= 10)
        val elevGain = MonoRepository.elevationGain(points.map { it.elevationMeters })
        assertTrue("Hike should have substantial elevation gain: $elevGain", elevGain > 100.0)
    }
}
