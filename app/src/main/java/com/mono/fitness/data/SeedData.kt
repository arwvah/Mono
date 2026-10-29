package com.mono.fitness.data

/**
 * Inserts realistic sample activities and a planned route on first launch
 * when the database is empty, allowing full testing of the map, elevation chart,
 * share card, PRs, and stats without physically walking outside.
 */
object SeedData {

    suspend fun seedIfEmpty(repo: MonoRepository) {
        if (repo.countActivities() > 0) return

        val now = System.currentTimeMillis()
        val oneDay = 86_400_000L

        // 1. Morning Run (Yesterday)
        val runStart = now - oneDay + 7 * 3600_000L // yesterday 7:00 AM
        val runDur = 26 * 60_000L // 26 min
        val runDist = 5200.0
        val runMoving = 25 * 60_000L + 30_000L
        val runPoints = generateRunTrack(runStart, runDur)

        repo.saveActivity(
            Activity(
                type = ActivityType.RUN.name,
                title = "Morning Park Loop",
                notes = "Crisp morning run. Felt strong on the uphill stretch.",
                distanceMeters = runDist,
                durationMillis = runDur,
                movingTimeMillis = runMoving,
                avgSpeedMps = runDist / (runMoving / 1000.0),
                maxSpeedMps = 4.8,
                elevationGainMeters = 48.0,
                calories = 385,
                avgHeartRate = 154,
                maxHeartRate = 172,
                startTimeMillis = runStart,
                endTimeMillis = runStart + runDur,
                isManual = false,
                source = "gps"
            ),
            runPoints
        )

        // 2. Sunset Coastal Ride (3 days ago)
        val rideStart = now - 3 * oneDay + 17 * 3600_000L // 3 days ago 5:00 PM
        val rideDur = 55 * 60_000L // 55 min
        val rideDist = 24_500.0
        val rideMoving = 52 * 60_000L
        val ridePoints = generateRideTrack(rideStart, rideDur)

        repo.saveActivity(
            Activity(
                type = ActivityType.RIDE.name,
                title = "Sunset Coastal Ride",
                notes = "Tailwind along the highway on the way back.",
                distanceMeters = rideDist,
                durationMillis = rideDur,
                movingTimeMillis = rideMoving,
                avgSpeedMps = rideDist / (rideMoving / 1000.0),
                maxSpeedMps = 12.6,
                elevationGainMeters = 165.0,
                calories = 590,
                avgHeartRate = 142,
                maxHeartRate = 168,
                startTimeMillis = rideStart,
                endTimeMillis = rideStart + rideDur,
                isManual = false,
                source = "gps"
            ),
            ridePoints
        )

        // 3. Mountain Trail Hike (5 days ago)
        val hikeStart = now - 5 * oneDay + 10 * 3600_000L // 5 days ago 10:00 AM
        val hikeDur = 100 * 60_000L // 1h 40m
        val hikeDist = 6_800.0
        val hikeMoving = 90 * 60_000L
        val hikePoints = generateHikeTrack(hikeStart, hikeDur)

        repo.saveActivity(
            Activity(
                type = ActivityType.HIKE.name,
                title = "Twin Peaks Summit Hike",
                notes = "Clear views across the bay. Steep rocky trail near the top.",
                distanceMeters = hikeDist,
                durationMillis = hikeDur,
                movingTimeMillis = hikeMoving,
                avgSpeedMps = hikeDist / (hikeMoving / 1000.0),
                maxSpeedMps = 2.4,
                elevationGainMeters = 310.0,
                calories = 540,
                avgHeartRate = 128,
                maxHeartRate = 155,
                startTimeMillis = hikeStart,
                endTimeMillis = hikeStart + hikeDur,
                isManual = false,
                source = "gps"
            ),
            hikePoints
        )

        // 4. Sample Planned Route
        val routeWaypoints = listOf(
            Triple(37.7989, -122.4662, 58.0),
            Triple(37.8020, -122.4635, 72.0),
            Triple(37.8055, -122.4680, 85.0),
            Triple(37.8080, -122.4745, 96.0),
            Triple(37.8045, -122.4810, 80.0),
            Triple(37.8010, -122.4780, 68.0),
            Triple(37.7975, -122.4720, 60.0),
            Triple(37.7989, -122.4662, 58.0)
        )
        val routeDist = MonoRepository.pathDistance(routeWaypoints.map { it.first to it.second })
        val routeElev = MonoRepository.elevationGain(routeWaypoints.map { it.third })

        repo.saveRoute(
            Route(
                name = "Presidio Vista Loop",
                activityType = ActivityType.RUN.name,
                distanceMeters = routeDist,
                elevationGainMeters = routeElev,
                notes = "Scenic loop through historic park trails with Golden Gate views."
            ),
            routeWaypoints.mapIndexed { idx, pt ->
                RoutePoint(
                    routeId = 0,
                    latitude = pt.first,
                    longitude = pt.second,
                    elevationMeters = pt.third,
                    sequence = idx
                )
            }
        )
    }

    internal fun generateRunTrack(startMs: Long, durationMs: Long): List<ActivityPoint> {
        // Closed loop in Golden Gate Park area
        val baseCoords = listOf(
            Pair(37.7690, -122.4835) to 48.0,
            Pair(37.7695, -122.4790) to 50.0,
            Pair(37.7702, -122.4735) to 54.0,
            Pair(37.7710, -122.4680) to 58.0,
            Pair(37.7715, -122.4620) to 62.0,
            Pair(37.7718, -122.4560) to 68.0,
            Pair(37.7695, -122.4550) to 75.0,
            Pair(37.7680, -122.4580) to 82.0,
            Pair(37.7675, -122.4640) to 78.0,
            Pair(37.7670, -122.4700) to 70.0,
            Pair(37.7668, -122.4760) to 62.0,
            Pair(37.7672, -122.4810) to 54.0,
            Pair(37.7685, -122.4830) to 49.0,
            Pair(37.7690, -122.4835) to 48.0
        )
        return interpolatePoints(baseCoords, startMs, durationMs, targetCount = 28)
    }

    internal fun generateRideTrack(startMs: Long, durationMs: Long): List<ActivityPoint> {
        val baseCoords = listOf(
            Pair(37.7715, -122.5110) to 12.0,
            Pair(37.7650, -122.5105) to 14.0,
            Pair(37.7550, -122.5090) to 11.0,
            Pair(37.7420, -122.5075) to 9.0,
            Pair(37.7300, -122.5060) to 8.0,
            Pair(37.7180, -122.5030) to 15.0,
            Pair(37.7050, -122.4980) to 25.0,
            Pair(37.6980, -122.4920) to 45.0,
            Pair(37.7050, -122.4980) to 25.0,
            Pair(37.7180, -122.5030) to 15.0,
            Pair(37.7300, -122.5060) to 8.0,
            Pair(37.7420, -122.5075) to 9.0,
            Pair(37.7550, -122.5090) to 11.0,
            Pair(37.7650, -122.5105) to 14.0,
            Pair(37.7715, -122.5110) to 12.0
        )
        return interpolatePoints(baseCoords, startMs, durationMs, targetCount = 42)
    }

    internal fun generateHikeTrack(startMs: Long, durationMs: Long): List<ActivityPoint> {
        val baseCoords = listOf(
            Pair(37.7515, -122.4475) to 135.0,
            Pair(37.7525, -122.4465) to 155.0,
            Pair(37.7535, -122.4455) to 180.0,
            Pair(37.7542, -122.4460) to 215.0,
            Pair(37.7546, -122.4470) to 255.0,
            Pair(37.7544, -122.4480) to 276.0, // North Peak
            Pair(37.7538, -122.4478) to 260.0,
            Pair(37.7530, -122.4475) to 278.0, // South Peak
            Pair(37.7520, -122.4470) to 230.0,
            Pair(37.7515, -122.4475) to 135.0
        )
        return interpolatePoints(baseCoords, startMs, durationMs, targetCount = 30)
    }

    private fun interpolatePoints(
        base: List<Pair<Pair<Double, Double>, Double>>,
        startMs: Long,
        durationMs: Long,
        targetCount: Int
    ): List<ActivityPoint> {
        val count = targetCount.coerceAtLeast(base.size)
        val result = mutableListOf<ActivityPoint>()

        for (i in 0 until count) {
            val progress = i.toDouble() / (count - 1).coerceAtLeast(1)
            val baseIndexFloat = progress * (base.size - 1)
            val lower = baseIndexFloat.toInt()
            val upper = (lower + 1).coerceAtMost(base.size - 1)
            val frac = baseIndexFloat - lower

            val (lowerCoord, lowerEle) = base[lower]
            val (upperCoord, upperEle) = base[upper]

            val lat = lowerCoord.first + frac * (upperCoord.first - lowerCoord.first)
            val lon = lowerCoord.second + frac * (upperCoord.second - lowerCoord.second)
            val ele = lowerEle + frac * (upperEle - lowerEle)
            val pointTime = if (i == count - 1) startMs + durationMs else startMs + (i * durationMs / (count - 1))

            result.add(
                ActivityPoint(
                    activityId = 0,
                    latitude = lat,
                    longitude = lon,
                    elevationMeters = ele,
                    timestampMillis = pointTime,
                    sequence = i
                )
            )
        }
        return result
    }
}
