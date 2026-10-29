package com.mono.fitness.util

import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

object Gpx {
    private val isoFormatter = DateTimeFormatter.ISO_INSTANT

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
            p.third?.let { w.append("<ele>${String.format(Locale.US, "%.1f", it)}</ele>") }
            times?.getOrNull(i)?.let {
                val formattedTime = isoFormatter.format(Instant.ofEpochMilli(it))
                w.append("<time>$formattedTime</time>")
            }
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
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(input)

        val nameNode = doc.getElementsByTagName("name").item(0)
        val name = nameNode?.textContent?.trim().takeUnless { it.isNullOrEmpty() } ?: "Imported"

        val typeNode = doc.getElementsByTagName("type").item(0)
        val type = typeNode?.textContent?.trim().takeUnless { it.isNullOrEmpty() }

        val points = mutableListOf<ActivityPoint>()
        val trkpts = doc.getElementsByTagName("trkpt")
        val targetNodes = if (trkpts.length > 0) trkpts else doc.getElementsByTagName("wpt")

        for (i in 0 until targetNodes.length) {
            val el = targetNodes.item(i) as? Element ?: continue
            val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
            val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue
            var ele: Double? = null
            var pointTime = System.currentTimeMillis()

            val children = el.childNodes
            for (j in 0 until children.length) {
                val child = children.item(j)
                if (child.nodeType == Node.ELEMENT_NODE) {
                    when (child.nodeName.lowercase(Locale.US)) {
                        "ele" -> ele = child.textContent?.trim()?.toDoubleOrNull()
                        "time" -> child.textContent?.trim()?.let {
                            parseTimestamp(it)?.let { pt -> pointTime = pt }
                        }
                    }
                }
            }

            points += ActivityPoint(
                activityId = 0,
                latitude = lat,
                longitude = lon,
                elevationMeters = ele,
                timestampMillis = pointTime,
                sequence = points.size
            )
        }

        return ImportedTrack(name, type, points)
    }

    fun parseTimestamp(s: String): Long? {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return null
        return try {
            OffsetDateTime.parse(trimmed).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                Instant.parse(trimmed).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;")
}
