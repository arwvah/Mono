package com.mono.fitness.util

import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class GpxRoundTripTest {

    @Test
    fun exportAndReimport_preservesPoints() {
        val points = listOf(
            ActivityPoint(activityId = 1, latitude = 1.0, longitude = 2.0, elevationMeters = 10.0, timestampMillis = 1000L, sequence = 0),
            ActivityPoint(
                activityId = 1,
                latitude = 1.1,
                longitude = 2.1,
                elevationMeters = 12.0,
                timestampMillis = 2000L,
                sequence = 1
            )
        )
        val out = ByteArrayOutputStream()
        Gpx.export(
            activity = Activity(
                type = "RUN",
                title = "Test",
                startTimeMillis = 1000L,
                endTimeMillis = 2000L
            ),
            points = points,
            out = out
        )
        val imported = Gpx.import(ByteArrayInputStream(out.toByteArray()))
        assertEquals("Test", imported.name)
        assertEquals("RUN", imported.type)
        assertEquals(2, imported.points.size)
        assertEquals(1.0, imported.points[0].latitude, 0.0)
        assertEquals(10.0, imported.points[0].elevationMeters!!, 0.0)
        assertEquals(1000L, imported.points[0].timestampMillis)
    }

    @Test
    fun import_minimalGpx_doesNotCrash() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
  <trk><name>X</name></trk>
</gpx>""".trimIndent().toByteArray()
        val imported = Gpx.import(ByteArrayInputStream(xml))
        assertEquals("X", imported.name)
        assertTrue(imported.points.isEmpty())
    }

    @Test
    fun import_gpxWithMillisAndOffsets_parsesCorrectly() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Strava" xmlns="http://www.topografix.com/GPX/1/1">
  <trk>
    <name>Strava Ride</name>
    <type>RIDE</type>
    <trkseg>
      <trkpt lat="37.7749" lon="-122.4194">
        <ele>15.5</ele>
        <time>2026-10-29T10:15:30.500Z</time>
      </trkpt>
      <trkpt lat="37.7755" lon="-122.4180">
        <ele>18.2</ele>
        <time>2026-10-29T15:15:35+05:00</time>
      </trkpt>
    </trkseg>
  </trk>
</gpx>""".trimIndent().toByteArray()
        val imported = Gpx.import(ByteArrayInputStream(xml))
        assertEquals("Strava Ride", imported.name)
        assertEquals("RIDE", imported.type)
        assertEquals(2, imported.points.size)
        assertEquals(37.7749, imported.points[0].latitude, 1e-4)
        assertEquals(-122.4194, imported.points[0].longitude, 1e-4)
        assertEquals(15.5, imported.points[0].elevationMeters!!, 0.1)
        assertNotNull(imported.points[0].timestampMillis)
    }

    @Test
    fun exportRoute_matchesSchema() {
        val out = ByteArrayOutputStream()
        Gpx.exportRoute(
            out = out,
            name = "Route",
            type = "RUN",
            points = listOf(
                1.0 to 2.0,
                1.1 to 2.1
            ),
            elevations = listOf(
                5.0,
                null
            )
        )
        val xml = out.toString(Charsets.UTF_8.name())
        assertTrue(xml.contains("<name>Route</name>"))
        assertTrue(xml.contains("lat=\"1.0\""))
        assertTrue(xml.contains("<ele>5.0</ele>"))
        assertFalse(xml.contains("<ele>null</ele>"))
    }
}
