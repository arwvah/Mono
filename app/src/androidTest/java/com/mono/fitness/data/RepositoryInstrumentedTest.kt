package com.mono.fitness.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private lateinit var db: MonoDatabase
    private lateinit var repo: MonoRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MonoDatabase::class.java).build()
        repo = MonoRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun loweringRecord_promotesNextBestActivity() = runBlocking {
        val first = repo.saveActivity(Activity(distanceMeters = 5000.0))
        val second = repo.saveActivity(Activity(distanceMeters = 3000.0))
        repo.saveActivity(repo.getActivity(first)!!.copy(distanceMeters = 1000.0))
        val best = db.personalRecordDao().get("RUN", RecordKeys.LONGEST_DISTANCE)!!
        assertEquals(second, best.activityId)
        assertEquals(3000.0, best.value, 0.0)
    }

    @Test
    fun changingSport_removesOldSportRecords() = runBlocking {
        val id = repo.saveActivity(Activity(distanceMeters = 5000.0))
        repo.saveActivity(repo.getActivity(id)!!.copy(type = "RIDE"))
        assertNull(db.personalRecordDao().get("RUN", RecordKeys.LONGEST_DISTANCE))
        assertEquals(id, db.personalRecordDao().get("RIDE", RecordKeys.LONGEST_DISTANCE)!!.activityId)
        repo.deleteActivity(id)
        assertNull(db.personalRecordDao().get("RIDE", RecordKeys.LONGEST_DISTANCE))
    }

    @Test
    fun untimedGpx_hasNoInventedPaceRecord() = runBlocking {
        val points = listOf(point(0), point(10)).map { it.copy(timestampMillis = 1000L) }
        val id = repo.importGpxTrack("Untimed route", "RUN", points)
        val activity = repo.getActivity(id)!!
        assertTrue(activity.distanceMeters > 200.0)
        assertEquals(0L, activity.durationMillis)
        assertEquals(0.0, activity.avgSpeedMps, 0.0)
        assertNull(db.personalRecordDao().get("RUN", RecordKeys.FASTEST_PACE))
    }

    @Test
    fun recordingCheckpoints_preservePointsAndUserNotes() = runBlocking {
        val id = repo.saveRecordingProgress(Activity(title = "Run"), listOf(point(0)))
        repo.saveActivity(repo.getActivity(id)!!.copy(title = "Morning run", notes = "Keep this"))
        repo.saveRecordingProgress(Activity(id = id, distanceMeters = 200.0), listOf(point(1)))
        assertEquals(listOf(0, 1), repo.getPoints(id).map { it.sequence })
        assertEquals("Morning run", repo.getActivity(id)!!.title)
        assertEquals("Keep this", repo.getActivity(id)!!.notes)
        assertEquals(1, repo.countActivities())
    }

    @Test
    fun failedTrackReplacement_rollsBackMetadataPointsAndRecords() = runBlocking {
        val id = repo.saveActivity(Activity(title = "Original", distanceMeters = 1000.0), listOf(point(0)))
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_points BEFORE INSERT ON activity_points
            BEGIN SELECT RAISE(ABORT, 'Simulated storage failure'); END
        """.trimIndent())
        var failed = false
        try {
            repo.saveActivity(Activity(id = id, title = "Changed", distanceMeters = 5000.0), listOf(point(1)))
        } catch (_: Exception) { failed = true }
        assertTrue(failed)
        assertEquals("Original", repo.getActivity(id)!!.title)
        assertEquals(listOf(0), repo.getPoints(id).map { it.sequence })
        assertEquals(1000.0, db.personalRecordDao().get("RUN", RecordKeys.LONGEST_DISTANCE)!!.value, 0.0)
    }

    @Test
    fun clearAllData_canBeCalledFromMainDispatcher() = runBlocking(kotlinx.coroutines.Dispatchers.Main) {
        repo.saveActivity(Activity(distanceMeters = 5000.0), listOf(point(0)))
        repo.clearAllData()
        assertEquals(0, repo.countActivities())
        assertTrue(repo.getPoints(1).isEmpty())
    }

    private fun point(sequence: Int) = ActivityPoint(
        activityId = 0, latitude = 41.0, longitude = 69.0 + sequence * 0.001, timestampMillis = sequence * 1000L, sequence = sequence
    )
}
