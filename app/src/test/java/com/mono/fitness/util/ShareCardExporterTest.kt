package com.mono.fitness.util

import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShareCardExporterTest {

    @Test
    fun render_withPoints_returnsBitmap() {
        val activity = Activity(type = "RUN", title = "Card", startTimeMillis = 1000L)
        val points = listOf(
            ActivityPoint(activityId = 1, latitude = 0.0, longitude = 0.0, timestampMillis = 1000L, sequence = 0),
            ActivityPoint(activityId = 1, latitude = 0.001, longitude = 0.001, timestampMillis = 2000L, sequence = 1)
        )
        val bmp = ShareCardExporter.render(activity, points)
        assertEquals(1080, bmp.width)
        assertEquals(1350, bmp.height)
        assertFalse(bmp.isRecycled)
    }

    @Test
    fun render_withoutPoints_showsNoGpsTrack() {
        val activity = Activity(type = "RUN", title = "Empty", startTimeMillis = 1000L)
        val bmp = ShareCardExporter.render(activity, emptyList())
        assertFalse(bmp.isRecycled)
    }
}
