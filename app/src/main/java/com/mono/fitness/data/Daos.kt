package com.mono.fitness.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY startTimeMillis DESC")
    fun observeAll(): Flow<List<Activity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    fun observeById(id: Long): Flow<Activity?>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): Activity?

    @Query("SELECT * FROM activities WHERE startTimeMillis >= :from AND startTimeMillis < :to ORDER BY startTimeMillis DESC")
    fun observeInRange(from: Long, to: Long): Flow<List<Activity>>

    @Query("SELECT * FROM activities WHERE startTimeMillis >= :from AND startTimeMillis < :to")
    suspend fun getInRange(from: Long, to: Long): List<Activity>

    @Query("SELECT * FROM activities WHERE type = :type ORDER BY startTimeMillis DESC")
    fun observeByType(type: String): Flow<List<Activity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: Activity): Long

    @Update
    suspend fun update(activity: Activity)

    @Delete
    suspend fun delete(activity: Activity)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM activities")
    suspend fun count(): Int
}

@Dao
interface ActivityPointDao {
    @Query("SELECT * FROM activity_points WHERE activityId = :activityId ORDER BY sequence ASC, timestampMillis ASC")
    fun observeForActivity(activityId: Long): Flow<List<ActivityPoint>>

    @Query("SELECT * FROM activity_points WHERE activityId = :activityId ORDER BY sequence ASC, timestampMillis ASC")
    suspend fun getForActivity(activityId: Long): List<ActivityPoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<ActivityPoint>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: ActivityPoint): Long

    @Query("DELETE FROM activity_points WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: Long)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<Route>>

    @Query("SELECT * FROM routes WHERE id = :id")
    fun observeById(id: Long): Flow<Route?>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getById(id: Long): Route?

    @Query(
        """
        SELECT * FROM routes
        WHERE (:type IS NULL OR activityType = :type)
          AND (:minDistance IS NULL OR distanceMeters >= :minDistance)
          AND (:maxDistance IS NULL OR distanceMeters <= :maxDistance)
        ORDER BY createdAtMillis DESC
        """
    )
    fun observeFiltered(
        type: String?,
        minDistance: Double?,
        maxDistance: Double?
    ): Flow<List<Route>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: Route): Long

    @Update
    suspend fun update(route: Route)

    @Delete
    suspend fun delete(route: Route)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface RoutePointDao {
    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY sequence ASC")
    fun observeForRoute(routeId: Long): Flow<List<RoutePoint>>

    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY sequence ASC")
    suspend fun getForRoute(routeId: Long): List<RoutePoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<RoutePoint>)

    @Query("DELETE FROM route_points WHERE routeId = :routeId")
    suspend fun deleteForRoute(routeId: Long)
}

@Dao
interface PersonalRecordDao {
    @Query("SELECT * FROM personal_records ORDER BY achievedAtMillis DESC")
    fun observeAll(): Flow<List<PersonalRecord>>

    @Query("SELECT * FROM personal_records WHERE activityType = :type")
    fun observeByType(type: String): Flow<List<PersonalRecord>>

    @Query("SELECT * FROM personal_records WHERE activityType = :type AND recordKey = :key LIMIT 1")
    suspend fun get(type: String, key: String): PersonalRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PersonalRecord): Long

    @Query("DELETE FROM personal_records")
    suspend fun clear()
}
