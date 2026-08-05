package com.mono.fitness.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityTypeTest {

    @Test
    fun fromName_caseInsensitive() {
        assertEquals(ActivityType.RUN, ActivityType.fromName("run"))
        assertEquals(ActivityType.RIDE, ActivityType.fromName("Ride"))
    }

    @Test
    fun fromName_unknown_fallsBackToRun() {
        assertEquals(ActivityType.RUN, ActivityType.fromName("unknown"))
    }
}
