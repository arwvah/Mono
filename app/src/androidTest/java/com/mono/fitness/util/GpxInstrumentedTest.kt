package com.mono.fitness.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.xml.sax.SAXException

/** Verify the Android SAX implementation as well as the host JVM parser. */
@RunWith(AndroidJUnit4::class)
class GpxInstrumentedTest {
    @Test
    fun platformParser_readsPrefixedTracksAndRejectsDtds() {
        val imported = Gpx.import("""<g:gpx xmlns:g="http://www.topografix.com/GPX/1/1">
            <g:trk><g:name>Android track</g:name><g:trkseg>
            <g:trkpt lat="41" lon="69"><g:ele>12</g:ele></g:trkpt>
            </g:trkseg></g:trk></g:gpx>""".byteInputStream())
        assertEquals("Android track", imported.name)
        assertEquals(12.0, imported.points.single().elevationMeters!!, 0.0)
        assertThrows(SAXException::class.java) {
            Gpx.import("""<!DOCTYPE gpx [<!ENTITY x "blocked">]><gpx><name>&x;</name></gpx>""".byteInputStream())
        }
    }
}
