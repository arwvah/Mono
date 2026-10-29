package com.mono.fitness.tracking

import android.Manifest
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.mono.fitness.MainActivity
import com.mono.fitness.MonoApp
import com.mono.fitness.data.ActivityType
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackingServiceInstrumentedTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @Test
    fun duplicateStartAndImmediateFinish_saveExactlyOneActivity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = (context as MonoApp).repository
        repo.clearAllData()
        TrackingController.clearFinished()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity {
                    TrackingController.start(it, ActivityType.RUN)
                    TrackingController.start(it, ActivityType.RUN)
                    TrackingController.stop(it)
                    TrackingController.stop(it)
                }
                val finished = withTimeout(15_000) { TrackingController.finished.filterNotNull().first() }
                assertEquals(1, repo.countActivities())
                assertEquals(finished.activityId, repo.observeActivities().first().single().id)
                assertFalse(TrackingController.running.value)
                assertFalse(finished.recording)
                assertFalse(finished.saving)
            } finally {
                if (TrackingController.running.value) {
                    scenario.onActivity { TrackingController.stop(it) }
                    withTimeout(15_000) { TrackingController.running.filter { !it }.first() }
                }
                TrackingController.clearFinished()
            }
        }
    }
}
