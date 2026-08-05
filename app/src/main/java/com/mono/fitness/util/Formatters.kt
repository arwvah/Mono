package com.mono.fitness.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Formatters {
    private val dateTime = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
    private val dateOnly = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val dayMonth = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    fun dateTime(millis: Long): String = dateTime.format(Date(millis))
    fun date(millis: Long): String = dateOnly.format(Date(millis))
    fun dayMonth(millis: Long): String = dayMonth.format(Date(millis))

    fun distanceKm(meters: Double): String {
        return if (meters < 1000) {
            String.format(Locale.US, "%.0f m", meters)
        } else {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        }
    }

    fun duration(millis: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(millis)
        val m = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    /** min/km as m:ss /km */
    fun pace(minPerKm: Double?): String {
        if (minPerKm == null || minPerKm.isNaN() || minPerKm.isInfinite() || minPerKm <= 0) {
            return "—"
        }
        val totalSec = (minPerKm * 60).toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.US, "%d:%02d /km", m, s)
    }

    fun paceFromActivity(distanceM: Double, movingMs: Long): String {
        val p = if (distanceM > 1 && movingMs > 0) {
            (movingMs / 1000.0 / 60.0) / (distanceM / 1000.0)
        } else null
        return pace(p)
    }

    fun speedKmh(mps: Double): String =
        String.format(Locale.US, "%.1f km/h", mps * 3.6)

    fun elevation(meters: Double): String =
        String.format(Locale.US, "%.0f m", meters)

    /**
     * Planned routes often have no elevation until API/GPS fill-in.
     * When [unknownWhenZero] is true and gain is ≤ 0, show an em dash.
     */
    fun elevation(meters: Double, unknownWhenZero: Boolean): String {
        if (unknownWhenZero && meters <= 0.0) return "—"
        return elevation(meters)
    }

    fun heartRate(bpm: Int?): String = bpm?.let { "$it bpm" } ?: "—"
}
