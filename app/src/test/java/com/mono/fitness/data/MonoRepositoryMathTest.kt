package com.mono.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonoRepositoryMathTest {

    @Test
    fun haversine_samePoint_isZero() {
        val d = MonoRepository.haversineMeters(48.8566, 2.3522, 48.8566, 2.3522)
        assertEquals(0.0, d, 0.01)
    }

    @Test
    fun haversine_knownDistance_parisToApprox1km() {
        // ~1 km east of a point near Paris
        val d = MonoRepository.haversineMeters(48.8566, 2.3522, 48.8566, 2.3657)
        assertTrue("expected ~1km, got $d", d in 900.0..1100.0)
    }

    @Test
    fun pathDistance_twoPoints() {
        val pts = listOf(
            48.8566 to 2.3522,
            48.8566 to 2.3657
        )
        val d = MonoRepository.pathDistance(pts)
        assertTrue(d in 900.0..1100.0)
    }

    @Test
    fun pathDistance_emptyOrSingle_isZero() {
        assertEquals(0.0, MonoRepository.pathDistance(emptyList()), 0.0)
        assertEquals(0.0, MonoRepository.pathDistance(listOf(1.0 to 2.0)), 0.0)
    }

    @Test
    fun elevationGain_sumsOnlyPositiveDeltas() {
        val elevs = listOf<Double?>(100.0, 120.0, 110.0, 140.0, null, 150.0)
        // +20, ignore -10, +30, skip null (prev stays 140), +10 = 60
        assertEquals(60.0, MonoRepository.elevationGain(elevs), 0.01)
    }

    @Test
    fun elevationGain_allNull_isZero() {
        assertEquals(0.0, MonoRepository.elevationGain(listOf(null, null)), 0.0)
    }

    @Test
    fun estimateCalories_run_positive() {
        val cal = MonoRepository.estimateCalories(
            ActivityType.RUN,
            distanceM = 5000.0,
            durationMs = 30 * 60_000L
        )
        assertTrue("calories=$cal", cal > 50)
    }

    @Test
    fun estimateCalories_zeroDuration_isZero() {
        assertEquals(0, MonoRepository.estimateCalories(ActivityType.WALK, 1000.0, 0L))
    }

    @Test
    fun paceMinPerKm_valid() {
        // 5 km in 25 min = 5.0 min/km
        val pace = MonoRepository.paceMinPerKm(5000.0, 25 * 60_000L)
        assertEquals(5.0, pace!!, 0.01)
    }

    @Test
    fun paceMinPerKm_invalid_returnsNull() {
        assertNull(MonoRepository.paceMinPerKm(0.0, 1000L))
        assertNull(MonoRepository.paceMinPerKm(1000.0, 0L))
    }

    @Test
    fun heartRateParse_uint8() {
        // flags=0 (uint8), bpm=142
        val bpm = com.mono.fitness.tracking.HeartRateBleClient.parseHeartRate(
            byteArrayOf(0x00, 142.toByte())
        )
        assertEquals(142, bpm)
    }

    @Test
    fun heartRateParse_uint16() {
        // flags=1 (uint16), bpm=180 little-endian
        val bpm = com.mono.fitness.tracking.HeartRateBleClient.parseHeartRate(
            byteArrayOf(0x01, 180.toByte(), 0x00)
        )
        assertEquals(180, bpm)
    }
}
