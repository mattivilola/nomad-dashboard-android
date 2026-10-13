package com.iloapps.nomaddashboard

import android.content.Intent
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.answers.NomadDeepLink
import com.iloapps.nomaddashboard.feature.dashboard.DashboardScrollTarget

/** Where an incoming `nomaddashboard://` link takes the single activity. */
internal sealed interface DeepLinkRoute {
    /** The dashboard, with [scrollTarget]'s card expanded and scrolled to when there is one. */
    data class Dashboard(val scrollTarget: DashboardScrollTarget?) : DeepLinkRoute

    /** Settings on the page and group of [destination], or on its page list when there is none. */
    data class Settings(val destination: SettingsDestination?) : DeepLinkRoute
}

internal fun NomadDeepLink.toRoute(): DeepLinkRoute =
    when (this) {
        is NomadDeepLink.Dashboard -> DeepLinkRoute.Dashboard(card?.let(::DashboardScrollTarget))
        is NomadDeepLink.Settings -> DeepLinkRoute.Settings(destination)
    }

/**
 * The link in an intent's [action] and [dataString], or null for anything else. Only a VIEW intent
 * counts: launcher shortcuts and the widgets send one, a plain launch carries none.
 */
internal fun nomadDeepLinkOf(action: String?, dataString: String?): NomadDeepLink? =
    if (action == Intent.ACTION_VIEW && dataString != null) NomadDeepLink.parse(dataString) else null

internal fun Intent.nomadDeepLink(): NomadDeepLink? = nomadDeepLinkOf(action, dataString)
