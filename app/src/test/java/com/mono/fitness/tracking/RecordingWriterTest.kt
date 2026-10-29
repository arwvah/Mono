package com.mono.fitness.tracking

import com.mono.fitness.data.Activity
import com.mono.fitness.data.ActivityPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingWriterTest {
    private fun path(size: Int) = (0 until size).map {
        ActivityPoint(activityId = 0, latitude = 0.0, longitude = it * 0.001, timestampMillis = it * 1000L, sequence = it)
    }

    @Test
    fun immediateFinish_waitsForCreationAndReusesItsId() = runTest {
        val creationGate = CompletableDeferred<Unit>()
        val ids = mutableListOf<Long>()
        val writer = RecordingWriter(this, { activity, _ ->
            ids += activity.id
            if (activity.id == 0L) creationGate.await()
            42L
        })
        writer.save(Activity(), emptyList())
        val finish = writer.save(Activity(distanceMeters = 100.0), path(2))
        runCurrent()
        assertFalse(finish.isCompleted)
        assertEquals(listOf(0L), ids)
        creationGate.complete(Unit)
        assertEquals(42L, finish.await().getOrThrow())
        assertEquals(listOf(0L, 42L), ids)
    }

    @Test
    fun checkpoints_appendOnlyUnsavedPointsInOrder() = runTest {
        val batches = mutableListOf<List<Int>>()
        val distances = mutableListOf<Double>()
        val writer = RecordingWriter(this, { activity, points ->
            delay(10)
            distances += activity.distanceMeters
            batches += points.map { it.sequence }
            7L
        })
        writer.save(Activity(distanceMeters = 10.0), path(1))
        writer.save(Activity(distanceMeters = 20.0), path(3))
        writer.save(Activity(distanceMeters = 30.0), path(3)).await().getOrThrow()
        assertEquals(listOf(10.0, 20.0, 30.0), distances)
        assertEquals(listOf(listOf(0), listOf(1, 2), emptyList<Int>()), batches)
    }

    @Test
    fun failedCreation_nextCheckpointRetriesWithoutLosingPoints() = runTest {
        var attempts = 0
        val batches = mutableListOf<List<Int>>()
        val ids = mutableListOf<Long>()
        val writer = RecordingWriter(this, { activity, points ->
            ids += activity.id
            batches += points.map { it.sequence }
            if (++attempts == 1) error("disk full")
            9L
        })
        assertTrue(writer.save(Activity(), path(2)).await().isFailure)
        assertEquals(9L, writer.save(Activity(), path(3)).await().getOrThrow())
        assertEquals(listOf(0L, 0L), ids)
        assertEquals(listOf(listOf(0, 1), listOf(0, 1, 2)), batches)
    }

    @Test
    fun failedAppend_retriesOnlyPointsAfterLastSuccessfulCommit() = runTest {
        var attempts = 0
        val batches = mutableListOf<List<Int>>()
        val writer = RecordingWriter(this, { activity, points ->
            batches += points.map { it.sequence }
            if (++attempts == 2) error("write failed")
            if (activity.id == 0L) 4L else activity.id
        })
        writer.save(Activity(), path(1)).await().getOrThrow()
        assertTrue(writer.save(Activity(), path(2)).await().isFailure)
        writer.save(Activity(), path(3)).await().getOrThrow()
        assertEquals(listOf(listOf(0), listOf(1), listOf(1, 2)), batches)
    }

    @Test
    fun serviceScopeReplacement_retriesCancelledWriteWithExistingId() = runTest {
        val oldScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        var attempts = 0
        val batches = mutableListOf<List<Int>>()
        val ids = mutableListOf<Long>()
        val writer = RecordingWriter(oldScope, { activity, points ->
            batches += points.map { it.sequence }
            ids += activity.id
            if (++attempts == 2) gate.await()
            12L
        })
        writer.save(Activity(), path(1)).await().getOrThrow()
        writer.save(Activity(), path(2))
        runCurrent()
        oldScope.cancel()
        writer.rebind(this)
        assertEquals(12L, writer.save(Activity(), path(3)).await().getOrThrow())
        assertEquals(listOf(0L, 12L, 12L), ids)
        assertEquals(listOf(listOf(0), listOf(1), listOf(1, 2)), batches)
    }
}
