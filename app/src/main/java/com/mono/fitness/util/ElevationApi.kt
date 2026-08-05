package com.mono.fitness.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Fetches ground elevation (meters) for lat/lng pairs via the free Open-Meteo elevation API.
 * No API key required. Returns nulls on network/parse failure so callers can fall back to "—".
 */
object ElevationApi {
    private const val BATCH = 50
    private const val TIMEOUT_MS = 12_000

    suspend fun fetchElevations(points: List<Pair<Double, Double>>): List<Double?> =
        withContext(Dispatchers.IO) {
            if (points.isEmpty()) return@withContext emptyList()
            val result = MutableList<Double?>(points.size) { null }
            var i = 0
            while (i < points.size) {
                val end = minOf(i + BATCH, points.size)
                val slice = points.subList(i, end)
                val elevs = fetchBatch(slice)
                for (j in elevs.indices) {
                    result[i + j] = elevs[j]
                }
                i = end
            }
            result
        }

    private fun fetchBatch(points: List<Pair<Double, Double>>): List<Double?> {
        if (points.isEmpty()) return emptyList()
        return try {
            val lats = points.joinToString(",") { String.format(Locale.US, "%.6f", it.first) }
            val lons = points.joinToString(",") { String.format(Locale.US, "%.6f", it.second) }
            val url = URL(
                "https://api.open-meteo.com/v1/elevation?latitude=$lats&longitude=$lons"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    return List(points.size) { null }
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONObject(body).optJSONArray("elevation")
                    ?: return List(points.size) { null }
                List(points.size) { idx ->
                    if (idx < arr.length() && !arr.isNull(idx)) arr.getDouble(idx) else null
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            List(points.size) { null }
        }
    }
}
