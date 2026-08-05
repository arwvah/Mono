package com.mono.fitness.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun distanceKm_under1km_showsMeters() {
        assertEquals("999 m", Formatters.distanceKm(999.0))
    }

    @Test
    fun distanceKm_over1km_showsTwoDecimals() {
        assertEquals("1.50 km", Formatters.distanceKm(1500.0))
    }

    @Test
    fun duration_lessThanHour_showsMmSs() {
        assertEquals("5:00", Formatters.duration(5 * 60_000L))
    }

    @Test
    fun duration_overHour_showsHhMmSs() {
        assertEquals("1:05:00", Formatters.duration(65 * 60_000L))
    }

    @Test
    fun pace_nullable_returnsDash() {
        assertEquals("—", Formatters.pace(null))
    }

    @Test
    fun paceFromActivity_invalid_returnsDash() {
        assertEquals("—", Formatters.paceFromActivity(0.0, 1000L))
    }
}
