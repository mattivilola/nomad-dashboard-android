package com.iloapps.nomaddashboard.review

enum class ScreenshotReviewScreen(
    val routeName: String,
    val rootTag: String,
) {
    Dashboard(
        routeName = "dashboard",
        rootTag = "review-screen-dashboard",
    ),
    Settings(
        routeName = "settings",
        rootTag = "review-screen-settings",
    ),
    Visited(
        routeName = "visited",
        rootTag = "review-screen-visited",
    ),
    TimeTracking(
        routeName = "timetracking",
        rootTag = "review-screen-timetracking",
    ),
    About(
        routeName = "about",
        rootTag = "review-screen-about",
    ),
    ;

    fun fileName(theme: ScreenshotReviewTheme): String = "${routeName}-phone-${theme.fileSuffix}.png"

    companion object {
        const val ExtraScreen = "screenshot_review_screen"
        const val ExtraTheme = "screenshot_review_theme"
        const val RelativeDirectoryName = "review-screenshots"
        const val SharedExportDirectory = "/sdcard/Download/nomad-dashboard-review-screenshots"

        fun fromRouteName(value: String?): ScreenshotReviewScreen =
            entries.firstOrNull { it.routeName == value } ?: Dashboard
    }
}

enum class ScreenshotReviewTheme(
    val routeName: String,
    val fileSuffix: String,
    val darkTheme: Boolean,
) {
    Light(
        routeName = "light",
        fileSuffix = "light",
        darkTheme = false,
    ),
    Dark(
        routeName = "dark",
        fileSuffix = "dark",
        darkTheme = true,
    ),
    ;

    companion object {
        fun fromRouteName(value: String?): ScreenshotReviewTheme =
            entries.firstOrNull { it.routeName == value } ?: Light
    }
}
