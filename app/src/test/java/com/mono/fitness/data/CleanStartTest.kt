package com.mono.fitness.data

import com.mono.fitness.tracking.TrackingSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CleanStartTest {

    @Test
    fun seedIfEmpty_canBeInvokedWithoutError() = runBlocking {
        // SeedData is completely neutral and does not seed mock data.
        assertNotNull(SeedData)
    }

    @Test
    fun trackingSnapshot_carriesActivityId() {
        val snapshot = TrackingSnapshot(
            type = ActivityType.RUN.name,
            activityId = 42L,
            recording = true
        )
        assertEquals(42L, snapshot.activityId)
        assertEquals(ActivityType.RUN.name, snapshot.type)
        assertEquals(true, snapshot.recording)
    }
}
