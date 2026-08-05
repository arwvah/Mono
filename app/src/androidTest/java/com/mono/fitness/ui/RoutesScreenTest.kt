package com.mono.fitness.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mono.fitness.data.MonoDatabase
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.ui.screens.RoutesScreen
import com.mono.fitness.ui.theme.MonoTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoutesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: MonoDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MonoDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun routesShowsFiltersAndPlan() {
        val repo = MonoRepository(db)
        composeRule.setContent {
            MonoTheme {
                RoutesScreen(repo = repo, onOpen = {}, onCreate = {})
            }
        }
        composeRule.onNodeWithText("Routes").assertIsDisplayed()
        composeRule.onNodeWithText("Plan").assertIsDisplayed()
        composeRule.onNodeWithText("Max distance").assertIsDisplayed()
        composeRule.onNodeWithText("Any max").assertIsDisplayed()
    }
}
