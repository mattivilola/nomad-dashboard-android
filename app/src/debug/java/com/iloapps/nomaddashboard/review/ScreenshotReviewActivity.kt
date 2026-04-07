package com.iloapps.nomaddashboard.review

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadTheme
import com.iloapps.nomaddashboard.feature.about.AboutScreen
import com.iloapps.nomaddashboard.feature.dashboard.DashboardScreen
import com.iloapps.nomaddashboard.feature.settings.SettingsScreen
import com.iloapps.nomaddashboard.feature.timetracking.TimeTrackingScreen
import com.iloapps.nomaddashboard.feature.visited.VisitedScreen
import java.io.File

class ScreenshotReviewActivity : ComponentActivity() {
    private var currentScreen by mutableStateOf(ScreenshotReviewScreen.Dashboard)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        updateScreen(intent)
        enableEdgeToEdge()
        setContent {
            NomadTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
                    val screenModifier = Modifier
                        .fillMaxSize()
                        .testTag(currentScreen.rootTag)
                        .padding(horizontal = 16.dp)
                        .padding(paddingValues)

                    when (currentScreen) {
                        ScreenshotReviewScreen.Dashboard -> DashboardScreen(
                            state = ScreenshotReviewFixtures.dashboardState(),
                            onRefresh = {},
                            onOpenSettings = {},
                            onOpenVisited = {},
                            onOpenTimeTracking = {},
                            onOpenAbout = {},
                            modifier = screenModifier,
                        )

                        ScreenshotReviewScreen.Settings -> SettingsScreen(
                            uiState = ScreenshotReviewFixtures.settingsState(),
                            onUpdate = { _ -> },
                            onUpdateProviderCredentials = { _ -> },
                            modifier = screenModifier,
                        )

                        ScreenshotReviewScreen.Visited -> Box(modifier = screenModifier) {
                            VisitedScreen(
                                state = ScreenshotReviewFixtures.visitedState(),
                                hasLocationPermission = true,
                                hasMapsApiKey = false,
                                onRefresh = {},
                                onRequestLocationPermission = {},
                            )
                        }

                        ScreenshotReviewScreen.TimeTracking -> TimeTrackingScreen(
                            state = ScreenshotReviewFixtures.timeTrackingState(),
                            hasNotificationPermission = true,
                            onProjectSelected = { _ -> },
                            onProjectNameChanged = { _ -> },
                            onCreateProject = {},
                            onStartTracking = {},
                            onStopTracking = {},
                            modifier = screenModifier,
                        )

                        ScreenshotReviewScreen.About -> AboutScreen(
                            modifier = screenModifier,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateScreen(intent)
    }

    fun showScreen(screen: ScreenshotReviewScreen) {
        currentScreen = screen
    }

    fun screenshotOutputFile(fileName: String): File {
        val directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?.resolve(ScreenshotReviewScreen.RelativeDirectoryName)
            ?: File(filesDir, ScreenshotReviewScreen.RelativeDirectoryName)
        directory.mkdirs()
        return File(directory, fileName)
    }

    private fun updateScreen(intent: Intent?) {
        currentScreen = ScreenshotReviewScreen.fromRouteName(
            intent?.getStringExtra(ScreenshotReviewScreen.ExtraScreen),
        )
    }

    companion object {
        fun intent(context: Context, screen: ScreenshotReviewScreen): Intent =
            Intent(context, ScreenshotReviewActivity::class.java).apply {
                putExtra(ScreenshotReviewScreen.ExtraScreen, screen.routeName)
            }
    }
}
