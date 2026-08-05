package com.mono.fitness.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mono.fitness.data.MonoDatabase
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.ui.screens.HomeScreen
import com.mono.fitness.ui.theme.MonoTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: MonoDatabase
    private lateinit var repo: MonoRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MonoDatabase::class.java
        ).allowMainThreadQueries().build()
        repo = MonoRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun homeShowsTitleAndEmptyState() {
        composeRule.setContent {
            MonoTheme {
                HomeScreen(
                    repo = repo,
                    onOpenActivity = {},
                    onAddManual = {},
                    onOpenSettings = {}
                )
            }
        }
        composeRule.onNodeWithText("Mono").assertIsDisplayed()
        composeRule.onNodeWithText("Your activity feed").assertIsDisplayed()
        composeRule.onNodeWithText("Add manual").assertIsDisplayed()
    }
}
