package com.mono.fitness.util

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationApiTest {

    @Test
    fun fetchElevations_empty_returnsEmpty() = runBlocking {
        val result = ElevationApi.fetchElevations(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchElevations_singlePoint_returnsList() = runBlocking {
        val result = ElevationApi.fetchElevations(listOf(48.8566 to 2.3522))
        assertEquals(1, result.size)
        assertTrue(result[0] == null || result[0]!! > -500)
    }

    @Test
    fun fetchElevations_manyPoints_capped() = runBlocking {
        val pts = (0 until 60).map { i -> (48.0 + i * 0.001) to (2.0 + i * 0.001) }
        val result = ElevationApi.fetchElevations(pts)
        assertEquals(60, result.size)
    }
}
