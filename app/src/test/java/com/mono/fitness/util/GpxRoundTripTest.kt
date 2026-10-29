package com.mono.fitness.util

import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class GpxRoundTripTest {

    @Test
    fun import_prefixedRoute_supportsRteptAndEscapedNames() {
        val xml = """<g:gpx xmlns:g="http://www.topografix.com/GPX/1/1">
            <g:rte><g:name>A &amp; B</g:name><g:type>RIDE</g:type>
            <g:rtept lat="41.0" lon="69.0"><g:ele>12.5</g:ele></g:rtept>
            <g:rtept lat="41.1" lon="69.1"/></g:rte></g:gpx>"""
        val imported = Gpx.import(xml.byteInputStream())
        assertEquals("A & B", imported.name)
        assertEquals("RIDE", imported.type)
        assertEquals(2, imported.points.size)
        assertEquals(12.5, imported.points.first().elevationMeters!!, 0.0)
        assertEquals(listOf(0, 1), imported.points.map { it.sequence })
    }

    @Test
    fun import_invalidCoordinatesAreSkippedAndNonfiniteElevationIsIgnored() {
        val xml = """<gpx><trk><trkseg>
            <trkpt lat="NaN" lon="69"/><trkpt lat="91" lon="69"/>
            <trkpt lat="41" lon="Infinity"/><trkpt lat="41" lon="181"/>
            <trkpt lat="41" lon="69"><ele>NaN</ele></trkpt>
            </trkseg></trk></gpx>"""
        val imported = Gpx.import(xml.byteInputStream())
        assertEquals(1, imported.points.size)
        assertEquals(null, imported.points.single().elevationMeters)
    }

    @Test
    fun import_rejectsDoctypeAndExternalEntities() {
        listOf(
            """<!DOCTYPE gpx [<!ENTITY secret SYSTEM "file:///nonexistent-mono-test">]>""",
            """<!DOCTYPE gpx [<!ENTITY secret "expanded">]>""",
            """<!DOCTYPE gpx SYSTEM "https://example.invalid/mono-test.dtd">"""
        ).forEach { declaration ->
            assertThrows(org.xml.sax.SAXException::class.java) {
                Gpx.import((declaration + "<gpx><name>&secret;</name></gpx>").byteInputStream())
            }
        }
    }

    @Test
    fun import_rejectsNonGpxDocuments() {
        assertThrows(org.xml.sax.SAXException::class.java) {
            Gpx.import("<html><name>Not a GPX file</name></html>".byteInputStream())
        }
    }

    @Test
    fun import_limitsBytesAndPointCount() {
        assertThrows(java.io.IOException::class.java) {
            Gpx.import(("<gpx>" + " ".repeat(Gpx.MAX_IMPORT_BYTES) + "</gpx>").byteInputStream())
        }
        assertThrows(org.xml.sax.SAXException::class.java) {
            Gpx.import(("<gpx>" + "<wpt lat=\"1\" lon=\"2\"/>".repeat(Gpx.MAX_IMPORT_POINTS + 1) + "</gpx>").byteInputStream())
        }
    }

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
