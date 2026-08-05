package com.mono.fitness.data

enum class ActivityType(val label: String, val iconHint: String) {
    RUN("Run", "directions_run"),
    RIDE("Ride", "directions_bike"),
    WALK("Walk", "directions_walk"),
    HIKE("Hike", "terrain"),
    SWIM("Swim", "pool"),
    GYM("Gym", "fitness_center");

    companion object {
        fun fromName(name: String): ActivityType {
            val n = name.trim().lowercase()
            entries.find { it.name.equals(n, ignoreCase = true) }?.let { return it }
            entries.find { it.label.equals(n, ignoreCase = true) }?.let { return it }
            return when {
                n.contains("run") || n.contains("jog") -> RUN
                n.contains("ride") || n.contains("cycl") || n.contains("bike") -> RIDE
                n.contains("walk") -> WALK
                n.contains("hik") || n.contains("trail") -> HIKE
                n.contains("swim") -> SWIM
                n.contains("gym") || n.contains("weight") || n.contains("strength") -> GYM
                else -> RUN
            }
        }
    }
}
