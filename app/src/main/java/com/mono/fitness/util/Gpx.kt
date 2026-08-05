package com.mono.fitness.util

import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Gpx {
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun export(
        activity: Activity,
        points: List<ActivityPoint>,
        out: OutputStream
    ) {
        writeTrack(
            name = activity.title.ifBlank { activity.type },
            type = activity.type,
            points = points.map { Triple(it.latitude, it.longitude, it.elevationMeters) },
            times = points.map { it.timestampMillis },
            out = out
        )
    }

    fun exportRoute(
        name: String,
        type: String,
        points: List<Pair<Double, Double>>,
        elevations: List<Double?> = emptyList(),
        out: OutputStream
    ) {
        writeTrack(
            name = name,
            type = type,
            points = points.mapIndexed { i, p ->
                Triple(p.first, p.second, elevations.getOrNull(i))
            },
            times = null,
            out = out
        )
    }

    private fun writeTrack(
        name: String,
        type: String,
        points: List<Triple<Double, Double, Double?>>,
        times: List<Long>?,
        out: OutputStream
    ) {
        val w = OutputStreamWriter(out, Charsets.UTF_8)
        w.append("""<?xml version="1.0" encoding="UTF-8"?>""")
        w.append("\n<gpx version=\"1.1\" creator=\"Mono\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        w.append("  <metadata><name>${escape(name)}</name></metadata>\n")
        w.append("  <trk>\n")
        w.append("    <name>${escape(name)}</name>\n")
        w.append("    <type>${escape(type)}</type>\n")
        w.append("    <trkseg>\n")
        for ((i, p) in points.withIndex()) {
            w.append("      <trkpt lat=\"${p.first}\" lon=\"${p.second}\">")
            p.third?.let { w.append("<ele>$it</ele>") }
            times?.getOrNull(i)?.let { w.append("<time>${iso.format(Date(it))}</time>") }
            w.append("</trkpt>\n")
        }
        w.append("    </trkseg>\n  </trk>\n</gpx>\n")
        w.flush()
    }

    data class ImportedTrack(
        val name: String,
        val type: String?,
        val points: List<ActivityPoint>
    )

    fun import(input: InputStream): ImportedTrack {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")
        val points = mutableListOf<ActivityPoint>()
        var name = "Imported"
        var type: String? = null
        var lat = 0.0
        var lon = 0.0
        var ele: Double? = null
        var time = System.currentTimeMillis()
        var inName = false
        var inType = false
        var inEle = false
        var inTime = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name.lowercase(Locale.US)) {
                    "trkpt", "wpt" -> {
                        lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                        lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                        ele = null
                    }
                    "name" -> inName = true
                    "type" -> inType = true
                    "ele" -> inEle = true
                    "time" -> inTime = true
                }
                XmlPullParser.TEXT -> {
                    val t = parser.text?.trim().orEmpty()
                    if (t.isEmpty()) {
                        // skip
                    } else if (inName) name = t
                    else if (inType) type = t
                    else if (inEle) ele = t.toDoubleOrNull()
                    else if (inTime) {
                        try {
                            time = iso.parse(t)?.time ?: time
                        } catch (_: Exception) {
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name.lowercase(Locale.US)) {
                    "trkpt", "wpt" -> {
                        points += ActivityPoint(
                            activityId = 0,
                            latitude = lat,
                            longitude = lon,
                            elevationMeters = ele,
                            timestampMillis = time,
                            sequence = points.size
                        )
                    }
                    "name" -> inName = false
                    "type" -> inType = false
                    "ele" -> inEle = false
                    "time" -> inTime = false
                }
            }
            event = parser.next()
        }
        return ImportedTrack(name, type, points)
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")
}
