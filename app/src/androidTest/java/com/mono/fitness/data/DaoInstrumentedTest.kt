package com.mono.fitness.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoInstrumentedTest {

    private lateinit var db: MonoDatabase
    private lateinit var activityDao: ActivityDao
    private lateinit var routeDao: RouteDao
    private lateinit var routePointDao: RoutePointDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MonoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activityDao = db.activityDao()
        routeDao = db.routeDao()
        routePointDao = db.routePointDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQueryActivity() = runBlocking {
        val id = activityDao.insert(
            Activity(
                type = ActivityType.RUN.name,
                title = "Test run",
                distanceMeters = 5000.0,
                durationMillis = 1_800_000L
            )
        )
        assertTrue(id > 0)
        val loaded = activityDao.getById(id)
        assertNotNull(loaded)
        assertEquals("Test run", loaded!!.title)
        assertEquals(1, activityDao.count())
    }

    @Test
    fun routeFilterByMaxDistance() = runBlocking {
        routeDao.insert(Route(name = "Short", distanceMeters = 3000.0))
        routeDao.insert(Route(name = "Long", distanceMeters = 15000.0))
        val filtered = routeDao.observeFiltered(
            type = null,
            minDistance = null,
            maxDistance = 5000.0
        ).first()
        assertEquals(1, filtered.size)
        assertEquals("Short", filtered[0].name)
    }

    @Test
    fun routePointsCascadeWithRoute() = runBlocking {
        val routeId = routeDao.insert(Route(name = "Trail", distanceMeters = 8000.0))
        routePointDao.insertAll(
            listOf(
                RoutePoint(routeId = routeId, latitude = 1.0, longitude = 2.0, sequence = 0),
                RoutePoint(routeId = routeId, latitude = 1.1, longitude = 2.1, sequence = 1)
            )
        )
        assertEquals(2, routePointDao.getForRoute(routeId).size)
        routeDao.deleteById(routeId)
        assertEquals(0, routePointDao.getForRoute(routeId).size)
    }
}
