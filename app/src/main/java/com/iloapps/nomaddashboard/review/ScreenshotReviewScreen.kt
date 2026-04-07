package com.iloapps.nomaddashboard.review

enum class ScreenshotReviewScreen(
    val routeName: String,
    val rootTag: String,
    val fileName: String,
) {
    Dashboard(
        routeName = "dashboard",
        rootTag = "review-screen-dashboard",
        fileName = "dashboard-phone.png",
    ),
    Settings(
        routeName = "settings",
        rootTag = "review-screen-settings",
        fileName = "settings-phone.png",
    ),
    Visited(
        routeName = "visited",
        rootTag = "review-screen-visited",
        fileName = "visited-phone.png",
    ),
    TimeTracking(
        routeName = "timetracking",
        rootTag = "review-screen-timetracking",
        fileName = "timetracking-phone.png",
    ),
    About(
        routeName = "about",
        rootTag = "review-screen-about",
        fileName = "about-phone.png",
    ),
    ;

    companion object {
        const val ExtraScreen = "screenshot_review_screen"
        const val RelativeDirectoryName = "review-screenshots"
        const val SharedExportDirectory = "/sdcard/Download/nomad-dashboard-review-screenshots"

        fun fromRouteName(value: String?): ScreenshotReviewScreen =
            entries.firstOrNull { it.routeName == value } ?: Dashboard
    }
}
