package com.iloapps.nomaddashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.AppearanceMode
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.feature.companion.CompanionRoute
import com.iloapps.nomaddashboard.feature.companion.CompanionRuntime
import com.iloapps.nomaddashboard.feature.companion.CompanionDashboardOverview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.answers.NomadDeepLink
import com.iloapps.nomaddashboard.feature.dashboard.DashboardScrollTarget
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.feature.about.AboutScreen
import com.iloapps.nomaddashboard.feature.dashboard.DashboardRoute
import com.iloapps.nomaddashboard.feature.settings.SettingsBaseRoute
import com.iloapps.nomaddashboard.feature.settings.SettingsDestinationArgument
import com.iloapps.nomaddashboard.feature.settings.SettingsRoute
import com.iloapps.nomaddashboard.feature.settings.SettingsRoutePattern
import com.iloapps.nomaddashboard.feature.settings.settingsDestinationFromArgument
import com.iloapps.nomaddashboard.feature.settings.settingsRoute
import com.iloapps.nomaddashboard.feature.timetracking.TimeTrackingRoute
import com.iloapps.nomaddashboard.feature.timetracking.runtime.TimeTrackingForegroundService
import com.iloapps.nomaddashboard.feature.visited.VisitedRoute
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var timeTrackingRepository: TimeTrackingRepository

    @Inject
    lateinit var dashboardRepository: NomadDashboardRepository

    /** The link that opened or re-opened the app and has not been shown yet. */
    private var pendingDeepLink by mutableStateOf<NomadDeepLink?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A recreated activity (rotation, theme change) keeps its own navigation state: only a new launch opens the link.
        if (savedInstanceState == null) pendingDeepLink = intent.nomadDeepLink()
        lifecycleScope.launch {
            if (
                TimeTrackingForegroundService.hasNotificationPermission(this@MainActivity) &&
                timeTrackingRepository.currentActiveEntry() != null
            ) {
                TimeTrackingForegroundService.start(this@MainActivity)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    dashboardRepository.refreshLocalSignals()
                    delay(if (dashboardRepository.snapshot.value.lowImpactMode) 60_000 else 30_000)
                }
            }
        }
        enableEdgeToEdge()
        setContent {
            val appSettings by dashboardRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            NomadTheme(
                darkTheme = when (appSettings.appearanceMode) {
                    AppearanceMode.SYSTEM -> isSystemInDarkTheme()
                    AppearanceMode.LIGHT -> false
                    AppearanceMode.DARK -> true
                },
            ) {
                val windowSizeClass = calculateWindowSizeClass(this)
                val snapshot by dashboardRepository.snapshot.collectAsStateWithLifecycle()
                NomadApp(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    snapshot = snapshot,
                    deepLink = pendingDeepLink,
                    onDeepLinkHandled = { pendingDeepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.nomadDeepLink()?.let { pendingDeepLink = it }
    }
}

private enum class TopDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
) {
    Dashboard("dashboard", "Dashboard", { Icon(Icons.Rounded.SpaceDashboard, contentDescription = null) }),
    Settings(SettingsBaseRoute, "Settings", { Icon(Icons.Rounded.Settings, contentDescription = null) }),
    Visited("visited", "Visited", { Icon(Icons.Rounded.Map, contentDescription = null) }),
    TimeTracking("timetracking", "Tracking", { Icon(Icons.Rounded.Timer, contentDescription = null) }),
    About("about", "About", { Icon(Icons.Rounded.Info, contentDescription = null) }),
}

@Composable
private fun NomadApp(
    widthSizeClass: WindowWidthSizeClass,
    snapshot: DashboardSnapshot,
    deepLink: NomadDeepLink?,
    onDeepLinkHandled: () -> Unit,
) {
    CompanionRuntime(snapshot)
    val navController = rememberNavController()
    var dashboardScrollTarget by remember { mutableStateOf<DashboardScrollTarget?>(null) }
    LaunchedEffect(deepLink) {
        when (val route = deepLink?.toRoute() ?: return@LaunchedEffect) {
            is DeepLinkRoute.Dashboard -> {
                navController.navigateToTopDestination(TopDestination.Dashboard)
                dashboardScrollTarget = route.scrollTarget
            }

            is DeepLinkRoute.Settings -> navController.navigateToSettings(route.destination)
        }
        onDeepLinkHandled()
    }
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
                            selected = currentDestination.isUnder(destination),
                            onClick = { navController.navigateToTopDestination(destination) },
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
                            selected = currentDestination.isUnder(destination),
                            onClick = { navController.navigateToTopDestination(destination) },
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
                    snapshot = snapshot,
                    isWideLayout = true,
                    dashboardScrollTarget = dashboardScrollTarget,
                    onDashboardScrollTargetHandled = { dashboardScrollTarget = null },
                )
            }
        } else {
            AppNavHost(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(paddingValues),
                navController = navController,
                snapshot = snapshot,
                isWideLayout = false,
                dashboardScrollTarget = dashboardScrollTarget,
                onDashboardScrollTargetHandled = { dashboardScrollTarget = null },
            )
        }
    }
}

private fun NavDestination?.isUnder(destination: TopDestination): Boolean =
    this?.hierarchy?.any { it.route?.substringBefore('?') == destination.route } == true

private fun NavHostController.navigateToTopDestination(destination: TopDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Opens Settings on its own page list, or on [destination]'s page scrolled to its group. A deep link
 * must not restore the saved Settings tab state, which would bring back the previous page.
 */
private fun NavHostController.navigateToSettings(destination: SettingsDestination?) {
    if (destination == null) {
        navigateToTopDestination(TopDestination.Settings)
    } else {
        navigate(settingsRoute(destination)) { launchSingleTop = true }
    }
}

@Composable
private fun AppNavHost(
    modifier: Modifier,
    navController: NavHostController,
    snapshot: DashboardSnapshot,
    isWideLayout: Boolean,
    dashboardScrollTarget: DashboardScrollTarget?,
    onDashboardScrollTargetHandled: () -> Unit,
) {
    val context = LocalContext.current
    val openCompanion: () -> Unit = { navController.navigate("companion") { launchSingleTop = true } }
    NavHost(navController = navController, startDestination = TopDestination.Dashboard.route, modifier = modifier) {
        composable(TopDestination.Dashboard.route) {
            DashboardRoute(
                companionOverview = { CompanionDashboardOverview(snapshot, onOpenCompanion = openCompanion) },
                onStartForegroundTracking = { TimeTrackingForegroundService.start(context) },
                onStopForegroundTracking = { TimeTrackingForegroundService.stop(context) },
                onOpenSettings = { destination -> navController.navigateToSettings(destination) },
                onOpenVisited = { navController.navigateToTopDestination(TopDestination.Visited) },
                onOpenCompanion = openCompanion,
                isWideLayout = isWideLayout,
                scrollTarget = dashboardScrollTarget,
                onScrollTargetHandled = onDashboardScrollTargetHandled,
            )
        }
        composable("companion") { CompanionRoute(snapshot, onBack = { navController.popBackStack() }) }
        composable(
            route = SettingsRoutePattern,
            arguments = listOf(
                navArgument(SettingsDestinationArgument) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            SettingsRoute(
                destination = settingsDestinationFromArgument(entry.arguments?.getString(SettingsDestinationArgument)),
                isWideLayout = isWideLayout,
                onOpenCompanion = openCompanion,
                onOpenVisitedMap = { navController.navigateToTopDestination(TopDestination.Visited) },
                onOpenAbout = { navController.navigateToTopDestination(TopDestination.About) },
            )
        }
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
