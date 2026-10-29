package com.mono.fitness.util

import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

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

    // Streaming parsing keeps large files from allocating an entire DOM tree.
    internal const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
    internal const val MAX_IMPORT_POINTS = 100_000

    fun import(input: InputStream): ImportedTrack {
        val handler = ImportHandler()
        val reader = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
        }.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.errorHandler = handler
        reader.entityResolver = handler
        // Reject DTDs before any internal or external entity can be expanded.
        reader.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        reader.parse(InputSource(LimitedInputStream(input)))
        val selected = handler.trackPoints.ifEmpty { handler.routePoints.ifEmpty { handler.waypoints } }
        return ImportedTrack(handler.name ?: "Imported", handler.type,
            selected.mapIndexed { i, p -> p.copy(sequence = i) })
    }

    private class ImportHandler : DefaultHandler2() {
        var name: String? = null
        var type: String? = null
        val trackPoints = mutableListOf<ActivityPoint>()
        val routePoints = mutableListOf<ActivityPoint>()
        val waypoints = mutableListOf<ActivityPoint>()
        private val stack = java.util.ArrayDeque<String>()
        private val text = StringBuilder()
        private var point: ActivityPoint? = null
        private var pointKind: String? = null
        private var pointCount = 0
        private val fallbackTime = System.currentTimeMillis()

        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            throw SAXException("GPX must not contain a DOCTYPE")
        }

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource {
            throw SAXException("External entities are not allowed in GPX")
        }

        override fun startElement(uri: String?, localName: String, qName: String?, attrs: Attributes) {
            if (stack.isEmpty() && localName != "gpx") throw SAXException("Expected a GPX document")
            stack.addLast(localName)
            text.setLength(0)
            if (localName in setOf("trkpt", "rtept", "wpt")) {
                if (++pointCount > MAX_IMPORT_POINTS) throw SAXException("GPX exceeds 100,000 points")
                pointKind = localName
                val lat = attrs.getValue("lat")?.toDoubleOrNull()
                val lon = attrs.getValue("lon")?.toDoubleOrNull()
                point = if (lat != null && lon != null && lat.isFinite() && lon.isFinite() &&
                    lat in -90.0..90.0 && lon in -180.0..180.0) {
                    ActivityPoint(activityId = 0, latitude = lat, longitude = lon,
                        timestampMillis = fallbackTime)
                } else null
            }
        }

        override fun characters(chars: CharArray, start: Int, length: Int) {
            text.append(chars, start, length)
        }

        override fun endElement(uri: String?, localName: String, qName: String?) {
            stack.removeLast()
            val parent = stack.peekLast().orEmpty()
            val value = text.toString().trim()
            when {
                localName == "name" && parent in setOf("metadata", "trk", "rte", "gpx") ->
                    if (name == null && value.isNotEmpty()) name = value
                localName == "type" && parent in setOf("trk", "rte") ->
                    if (type == null && value.isNotEmpty()) type = value
                localName == "ele" && parent == pointKind ->
                    point = point?.copy(elevationMeters = value.toDoubleOrNull()?.takeIf { it.isFinite() })
                localName == "time" && parent == pointKind ->
                    point = point?.copy(timestampMillis = parseTimestamp(value) ?: fallbackTime)
                localName == pointKind -> {
                    point?.let {
                        when (localName) {
                            "trkpt" -> trackPoints += it
                            "rtept" -> routePoints += it
                            else -> waypoints += it
                        }
                    }
                    point = null
                    pointKind = null
                }
            }
            text.setLength(0)
        }
    }

    private class LimitedInputStream(input: InputStream) : FilterInputStream(input) {
        private var count = 0
        override fun read(): Int = `in`.read().also { if (it != -1) account(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            `in`.read(buffer, offset, minOf(length, MAX_IMPORT_BYTES - count + 1)).also {
                if (it > 0) account(it)
            }
        private fun account(size: Int) {
            count += size
            if (count > MAX_IMPORT_BYTES) throw IOException("GPX exceeds 16 MiB")
        }
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
