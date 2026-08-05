package com.mono.fitness.data

import kotlinx.coroutines.flow.Flow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class MonoRepository(
    private val db: MonoDatabase
) {
    private val activities = db.activityDao()
    private val points = db.activityPointDao()
    private val routes = db.routeDao()
    private val routePoints = db.routePointDao()
    private val records = db.personalRecordDao()

    fun observeActivities(): Flow<List<Activity>> = activities.observeAll()
    fun observeActivity(id: Long): Flow<Activity?> = activities.observeById(id)
    fun observePoints(activityId: Long): Flow<List<ActivityPoint>> =
        points.observeForActivity(activityId)

    suspend fun getActivity(id: Long) = activities.getById(id)
    suspend fun getPoints(activityId: Long) = points.getForActivity(activityId)

    /**
     * @param track when non-null, replaces stored points (empty list clears the track).
     *              When null (default on metadata-only edits), existing GPS points are kept.
     */
    suspend fun saveActivity(
        activity: Activity,
        track: List<ActivityPoint>? = null
    ): Long {
        val id = if (activity.id == 0L) {
            activities.insert(activity)
        } else {
            activities.update(activity)
            activity.id
        }
        if (track != null) {
            points.deleteForActivity(id)
            if (track.isNotEmpty()) {
                points.insertAll(track.mapIndexed { i, p ->
                    p.copy(id = 0, activityId = id, sequence = i)
                })
            }
        }
        activities.getById(id)?.let { updatePersonalRecords(it) }
        return id
    }

    /**
     * Import a parsed GPX track as a new activity (distance/elev/duration from points).
     */
    suspend fun importGpxTrack(
        name: String,
        typeName: String?,
        rawPoints: List<ActivityPoint>
    ): Long {
        require(rawPoints.isNotEmpty()) { "GPX has no track points" }
        val type = ActivityType.fromName(typeName ?: ActivityType.RUN.name)
        val ordered = rawPoints.mapIndexed { i, p -> p.copy(sequence = i) }
        val dist = pathDistance(ordered.map { it.latitude to it.longitude })
        val elev = elevationGain(ordered.map { it.elevationMeters })
        val start = ordered.minOf { it.timestampMillis }
        val end = ordered.maxOf { it.timestampMillis }.coerceAtLeast(start)
        val dur = (end - start).coerceAtLeast(1L)
        val avg = dist / (dur / 1000.0)
        val cal = estimateCalories(type, dist, dur)
        return saveActivity(
            Activity(
                type = type.name,
                title = name.ifBlank { "Imported ${type.label}" },
                distanceMeters = dist,
                durationMillis = dur,
                movingTimeMillis = dur,
                avgSpeedMps = avg,
                maxSpeedMps = avg,
                elevationGainMeters = elev,
                calories = cal,
                startTimeMillis = start,
                endTimeMillis = end,
                isManual = false,
                source = "gpx"
            ),
            ordered
        )
    }

    suspend fun deleteActivity(id: Long) {
        activities.deleteById(id)
    }

    fun observeRoutes(
        type: String? = null,
        minDistance: Double? = null,
        maxDistance: Double? = null
    ): Flow<List<Route>> = routes.observeFiltered(type, minDistance, maxDistance)

    fun observeRoute(id: Long) = routes.observeById(id)
    fun observeRoutePoints(routeId: Long) = routePoints.observeForRoute(routeId)
    suspend fun getRoutePoints(routeId: Long) = routePoints.getForRoute(routeId)

    suspend fun saveRoute(route: Route, track: List<RoutePoint>): Long {
        val id = if (route.id == 0L) routes.insert(route) else {
            routes.update(route)
            routePoints.deleteForRoute(route.id)
            route.id
        }
        routePoints.insertAll(track.mapIndexed { i, p ->
            p.copy(id = 0, routeId = id, sequence = i)
        })
        return id
    }

    suspend fun deleteRoute(id: Long) = routes.deleteById(id)

    fun observePersonalRecords() = records.observeAll()

    suspend fun activitiesInRange(from: Long, to: Long) = activities.getInRange(from, to)

    private suspend fun updatePersonalRecords(activity: Activity) {
        val type = activity.type
        // Longest distance
        upsertIfBetter(
            type, RecordKeys.LONGEST_DISTANCE, activity.distanceMeters, "m",
            activity.id, higherIsBetter = true, label = "Longest distance"
        )
        // Most elevation
        upsertIfBetter(
            type, RecordKeys.MOST_ELEVATION, activity.elevationGainMeters, "m",
            activity.id, higherIsBetter = true, label = "Most elevation"
        )
        // Longest duration
        upsertIfBetter(
            type, RecordKeys.LONGEST_DURATION, activity.durationMillis.toDouble(), "ms",
            activity.id, higherIsBetter = true, label = "Longest duration"
        )
        // Fastest pace (min/km) — only if distance > 200m
        if (activity.distanceMeters > 200 && activity.movingTimeMillis > 0) {
            val paceMinPerKm =
                (activity.movingTimeMillis / 1000.0 / 60.0) / (activity.distanceMeters / 1000.0)
            upsertIfBetter(
                type, RecordKeys.FASTEST_PACE, paceMinPerKm, "min/km",
                activity.id, higherIsBetter = false, label = "Fastest pace"
            )
        }
        // Max heart rate
        activity.maxHeartRate?.let { hr ->
            upsertIfBetter(
                type, RecordKeys.MAX_HEART_RATE, hr.toDouble(), "bpm",
                activity.id, higherIsBetter = true, label = "Max heart rate"
            )
        }
    }

    private suspend fun upsertIfBetter(
        type: String,
        key: String,
        value: Double,
        unit: String,
        activityId: Long,
        higherIsBetter: Boolean,
        label: String
    ) {
        val existing = records.get(type, key)
        val better = when {
            existing == null -> true
            higherIsBetter -> value > existing.value
            else -> value < existing.value
        }
        if (better && value > 0) {
            records.upsert(
                PersonalRecord(
                    id = existing?.id ?: 0,
                    activityType = type,
                    recordKey = key,
                    value = value,
                    unit = unit,
                    activityId = activityId,
                    label = label
                )
            )
        }
    }

    suspend fun countActivities() = activities.count()

    suspend fun clearPersonalRecords() {
        records.clear()
    }

    /** Wipes all Room tables (activities, routes, PRs, points). Useful to re-seed without uninstall. */
    suspend fun clearAllData() {
        db.clearAllTables()
    }

    companion object {
        fun haversineMeters(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        fun pathDistance(points: List<Pair<Double, Double>>): Double {
            if (points.size < 2) return 0.0
            var total = 0.0
            for (i in 1 until points.size) {
                total += haversineMeters(
                    points[i - 1].first, points[i - 1].second,
                    points[i].first, points[i].second
                )
            }
            return total
        }

        fun elevationGain(elevations: List<Double?>): Double {
            var gain = 0.0
            var prev: Double? = null
            for (e in elevations) {
                if (e != null && prev != null) {
                    val d = e - prev
                    if (d > 0) gain += d
                }
                if (e != null) prev = e
            }
            return gain
        }

        fun estimateCalories(type: ActivityType, distanceM: Double, durationMs: Long): Int {
            val hours = durationMs / 3_600_000.0
            if (hours <= 0) return 0
            val met = when (type) {
                ActivityType.RUN -> 9.8
                ActivityType.RIDE -> 7.5
                ActivityType.WALK -> 3.5
                ActivityType.HIKE -> 6.0
                ActivityType.SWIM -> 8.0
                ActivityType.GYM -> 5.0
            }
            // Assume 70kg
            return (met * 70 * hours).toInt().coerceAtLeast(0)
        }

        fun paceMinPerKm(distanceM: Double, movingMs: Long): Double? {
            if (distanceM < 1 || movingMs <= 0) return null
            return (movingMs / 1000.0 / 60.0) / (distanceM / 1000.0)
        }

        fun clamp(v: Double, lo: Double, hi: Double) = max(lo, min(hi, v))
    }
}
