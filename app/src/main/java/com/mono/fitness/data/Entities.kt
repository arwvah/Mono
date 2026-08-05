package com.mono.fitness.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class Activity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = ActivityType.RUN.name,
    val title: String = "",
    val notes: String = "",
    /** Meters */
    val distanceMeters: Double = 0.0,
    /** Milliseconds moving + paused total wall clock for the session */
    val durationMillis: Long = 0,
    /** Moving time only */
    val movingTimeMillis: Long = 0,
    /** Meters per second average (moving) */
    val avgSpeedMps: Double = 0.0,
    val maxSpeedMps: Double = 0.0,
    /** Elevation gain in meters */
    val elevationGainMeters: Double = 0.0,
    val calories: Int = 0,
    val avgHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    /** Epoch millis start */
    val startTimeMillis: Long = System.currentTimeMillis(),
    val endTimeMillis: Long = System.currentTimeMillis(),
    val isManual: Boolean = true,
    val source: String = "manual"
)

@Entity(
    tableName = "activity_points",
    foreignKeys = [
        ForeignKey(
            entity = Activity::class,
            parentColumns = ["id"],
            childColumns = ["activityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("activityId")]
)
data class ActivityPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val timestampMillis: Long,
    val speedMps: Double? = null,
    val accuracyMeters: Float? = null,
    val sequence: Int = 0
)

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val activityType: String = ActivityType.RUN.name,
    /** JSON array of {lat,lng,ele?} or encoded polyline-like simple format — we store as points table instead */
    val distanceMeters: Double = 0.0,
    val elevationGainMeters: Double = 0.0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "route_points",
    foreignKeys = [
        ForeignKey(
            entity = Route::class,
            parentColumns = ["id"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routeId")]
)
data class RoutePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val sequence: Int = 0
)

@Entity(
    tableName = "personal_records",
    indices = [Index(value = ["activityType", "recordKey"], unique = true)]
)
data class PersonalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityType: String,
    /** e.g. FASTEST_PACE, LONGEST_DISTANCE, MOST_ELEVATION, LONGEST_DURATION */
    val recordKey: String,
    val value: Double,
    val unit: String,
    val activityId: Long?,
    val achievedAtMillis: Long = System.currentTimeMillis(),
    val label: String = ""
)

object RecordKeys {
    const val FASTEST_PACE = "FASTEST_PACE" // sec per km (lower better) — store as min/km
    const val LONGEST_DISTANCE = "LONGEST_DISTANCE" // meters
    const val MOST_ELEVATION = "MOST_ELEVATION" // meters
    const val LONGEST_DURATION = "LONGEST_DURATION" // millis
    const val MAX_HEART_RATE = "MAX_HEART_RATE" // bpm
}
