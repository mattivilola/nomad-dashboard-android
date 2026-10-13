package com.iloapps.nomaddashboard.feature.widgets.tile

import com.iloapps.nomaddashboard.core.travel.status.StatusLinePresentation

/** What the Quick Settings tile shows under its label. */
internal object TileSubtitle {
    /**
     * The status line's text ("30%", "Offline", "Wind 45", "58 ms", "21°"), or [appName] when no rule
     * applies. Which rule wins, and whether the top heads-up counts, is decided by
     * [StatusLinePresentation] and the user's "top heads-up" setting.
     */
    fun of(statusLine: StatusLinePresentation?, appName: String): String =
        statusLine?.text?.trim()?.takeIf(String::isNotEmpty) ?: appName
}
