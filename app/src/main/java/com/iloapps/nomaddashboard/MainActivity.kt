package com.iloapps.nomaddashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.feature.about.AboutScreen
import com.iloapps.nomaddashboard.feature.dashboard.DashboardRoute
import com.iloapps.nomaddashboard.feature.settings.SettingsRoute
import com.iloapps.nomaddashboard.feature.timetracking.TimeTrackingRoute
import com.iloapps.nomaddashboard.feature.timetracking.runtime.TimeTrackingForegroundService
import com.iloapps.nomaddashboard.feature.visited.VisitedRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var timeTrackingRepository: TimeTrackingRepository

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            if (
                TimeTrackingForegroundService.hasNotificationPermission(this@MainActivity) &&
                timeTrackingRepository.currentActiveEntry() != null
            ) {
                TimeTrackingForegroundService.start(this@MainActivity)
            }
        }
        enableEdgeToEdge()
        setContent {
            NomadTheme {
                val windowSizeClass = calculateWindowSizeClass(this)
                NomadApp(windowSizeClass.widthSizeClass)
            }
        }
    }
}

private enum class TopDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
) {
    Dashboard("dashboard", "Dashboard", { Icon(Icons.Rounded.SpaceDashboard, contentDescription = null) }),
    Settings("settings", "Settings", { Icon(Icons.Rounded.Settings, contentDescription = null) }),
    Visited("visited", "Visited", { Icon(Icons.Rounded.Map, contentDescription = null) }),
    TimeTracking("timetracking", "Tracking", { Icon(Icons.Rounded.Timer, contentDescription = null) }),
    About("about", "About", { Icon(Icons.Rounded.Info, contentDescription = null) }),
}

@Composable
private fun NomadApp(widthSizeClass: WindowWidthSizeClass) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val destinations = TopDestination.entries
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!useRail) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            modifier = Modifier.testTag("nav-${destination.route}"),
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = destination.icon,
                            label = { androidx.compose.material3.Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        if (useRail) {
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                NavigationRail {
                    destinations.forEach { destination ->
                        NavigationRailItem(
                            modifier = Modifier.testTag("nav-${destination.route}"),
                            selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = destination.icon,
                            label = { androidx.compose.material3.Text(destination.label) },
                        )
                    }
                }
                AppNavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    navController = navController,
                )
            }
        } else {
            AppNavHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(paddingValues),
                navController = navController,
            )
        }
    }
}

@Composable
private fun AppNavHost(
    modifier: Modifier,
    navController: androidx.navigation.NavHostController,
) {
    val context = LocalContext.current
    NavHost(navController = navController, startDestination = TopDestination.Dashboard.route, modifier = modifier) {
        composable(TopDestination.Dashboard.route) {
            DashboardRoute(
                onOpenSettings = { navController.navigate(TopDestination.Settings.route) },
                onOpenVisited = { navController.navigate(TopDestination.Visited.route) },
                onOpenTimeTracking = { navController.navigate(TopDestination.TimeTracking.route) },
                onOpenAbout = { navController.navigate(TopDestination.About.route) },
            )
        }
        composable(TopDestination.Settings.route) { SettingsRoute() }
        composable(TopDestination.Visited.route) { VisitedRoute() }
        composable(TopDestination.TimeTracking.route) {
            TimeTrackingRoute(
                onStartForegroundTracking = { TimeTrackingForegroundService.start(context) },
                onStopForegroundTracking = { TimeTrackingForegroundService.stop(context) },
            )
        }
        composable(TopDestination.About.route) { AboutScreen() }
    }
}
