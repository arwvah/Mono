package com.mono.fitness.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationApiTest {
    @Test
    fun emptyInput_doesNotRequestNetwork() = runBlocking {
        val result = ElevationApi.fetchElevations(emptyList()) { error("No request expected") }
        assertTrue(result.isEmpty())
    }

    @Test
    fun batchesAtFiftyPointsAndPreservesOrder() = runBlocking {
        val points = (0 until 120).map { it.toDouble() to 2.0 }
        val sizes = mutableListOf<Int>()
        val result = ElevationApi.fetchElevations(points) { batch ->
            sizes += batch.size
            batch.map { it.first }
        }
        assertEquals(listOf(50, 50, 20), sizes)
        assertEquals(points.map { it.first }, result)
    }

    @Test
    fun shortResponse_preservesMissingValuesAndNextBatch() = runBlocking {
        val points = (0 until 60).map { it.toDouble() to 2.0 }
        val result = ElevationApi.fetchElevations(points) { listOf(12.0, null) }
        assertEquals(60, result.size)
        assertEquals(12.0, result[0])
        assertEquals(null, result[49])
        assertEquals(12.0, result[50])
        assertEquals(null, result[59])
    }
}
