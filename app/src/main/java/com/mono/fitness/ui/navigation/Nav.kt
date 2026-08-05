package com.mono.fitness.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mono.fitness.data.MonoRepository
import com.mono.fitness.ui.screens.ActivityDetailScreen
import com.mono.fitness.ui.screens.ActivityEditScreen
import com.mono.fitness.ui.screens.HomeScreen
import com.mono.fitness.ui.screens.RecordScreen
import com.mono.fitness.ui.screens.RouteDetailScreen
import com.mono.fitness.ui.screens.RouteEditScreen
import com.mono.fitness.ui.screens.RoutesScreen
import com.mono.fitness.ui.screens.SettingsScreen
import com.mono.fitness.ui.screens.StatsScreen
import com.mono.fitness.ui.theme.FrostedPanel
import com.mono.fitness.ui.theme.PillShape

sealed class Dest(
    val route: String,
    val label: String,
    val icon: ImageVector? = null
) {
    data object Home : Dest("home", "Home", Icons.Outlined.Home)
    data object Record : Dest("record", "Record", Icons.Outlined.RadioButtonChecked)
    data object Routes : Dest("routes", "Routes", Icons.Outlined.Map)
    data object Stats : Dest("stats", "Stats", Icons.Outlined.Insights)
    data object ActivityDetail : Dest("activity/{id}", "Activity") {
        fun create(id: Long) = "activity/$id"
    }
    data object ActivityEdit : Dest("activity_edit?id={id}", "Edit") {
        fun create(id: Long? = null) =
            if (id == null) "activity_edit?id=-1" else "activity_edit?id=$id"
    }
    data object RouteDetail : Dest("route/{id}", "Route") {
        fun create(id: Long) = "route/$id"
    }
    data object RouteEdit : Dest("route_edit", "Plan route")
    data object Settings : Dest("settings", "Settings")
}

private val bottomTabs = listOf(Dest.Home, Dest.Record, Dest.Routes, Dest.Stats)

@Composable
fun MonoNav(repo: MonoRepository) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBottom = bottomTabs.any { current == it.route }

    Scaffold(
        containerColor = Color.Transparent
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = Dest.Home.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    fadeIn(tween(300))
                },
                exitTransition = {
                    fadeOut(tween(250))
                },
                popEnterTransition = {
                    fadeIn(tween(300))
                },
                popExitTransition = { fadeOut(tween(250)) }
            ) {
                composable(Dest.Home.route) {
                    HomeScreen(
                        repo = repo,
                        onOpenActivity = { nav.navigate(Dest.ActivityDetail.create(it)) },
                        onAddManual = { nav.navigate(Dest.ActivityEdit.create(null)) },
                        onOpenSettings = { nav.navigate(Dest.Settings.route) }
                    )
                }
                composable(Dest.Record.route) {
                    RecordScreen(
                        repo = repo,
                        onSaved = { id ->
                            nav.navigate(Dest.ActivityDetail.create(id)) {
                                popUpTo(Dest.Record.route)
                            }
                        }
                    )
                }
                composable(Dest.Routes.route) {
                    RoutesScreen(
                        repo = repo,
                        onOpen = { nav.navigate(Dest.RouteDetail.create(it)) },
                        onCreate = { nav.navigate(Dest.RouteEdit.route) }
                    )
                }
                composable(Dest.Stats.route) {
                    StatsScreen(repo = repo)
                }
                composable(
                    Dest.ActivityDetail.route,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: return@composable
                    ActivityDetailScreen(
                        repo = repo,
                        activityId = id,
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate(Dest.ActivityEdit.create(id)) }
                    )
                }
                composable(
                    Dest.ActivityEdit.route,
                    arguments = listOf(
                        navArgument("id") {
                            type = NavType.LongType
                            defaultValue = -1L
                        }
                    )
                ) { entry ->
                    val raw = entry.arguments?.getLong("id") ?: -1L
                    val id = raw.takeIf { it > 0 }
                    ActivityEditScreen(
                        repo = repo,
                        activityId = id,
                        onDone = { savedId ->
                            nav.popBackStack()
                            if (savedId != null) {
                                nav.navigate(Dest.ActivityDetail.create(savedId))
                            }
                        },
                        onCancel = { nav.popBackStack() }
                    )
                }
                composable(
                    Dest.RouteDetail.route,
                    arguments = listOf(navArgument("id") { type = NavType.LongType })
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: return@composable
                    RouteDetailScreen(
                        repo = repo,
                        routeId = id,
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Dest.RouteEdit.route) {
                    RouteEditScreen(
                        repo = repo,
                        onDone = { id ->
                            nav.popBackStack()
                            nav.navigate(Dest.RouteDetail.create(id))
                        },
                        onCancel = { nav.popBackStack() }
                    )
                }
                composable(Dest.Settings.route) {
                    SettingsScreen(
                        repo = repo,
                        onBack = { nav.popBackStack() }
                    )
                }
            }

            if (showBottom) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                ) {
                    FloatingNavBar(
                        tabs = bottomTabs,
                        currentRoute = current,
                        onTabClick = { dest ->
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingNavBar(
    tabs: List<Dest>,
    currentRoute: String?,
    onTabClick: (Dest) -> Unit
) {
    FrostedPanel(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = PillShape,
        elevated = true
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { dest ->
                val selected = currentRoute == dest.route
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onSurface 
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), 
                    label = "navColor"
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { onTabClick(dest) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = dest.icon!!,
                        contentDescription = dest.label,
                        modifier = Modifier.size(24.dp),
                        tint = color
                    )
                }
            }
        }
    }
}
