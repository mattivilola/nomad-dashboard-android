package com.iloapps.nomaddashboard.core.travel.answers

import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * Links that open the app on a screen: launcher shortcuts and widget taps use them. Ported from the
 * macOS `NomadDeepLink`, which opens the dashboard; Android also opens a card and a Settings group.
 *
 * The URI is `<scheme>://<target>[/<name>]`, with the scheme `nomaddashboard` for the release app. A
 * build that must not answer the release app's links (for example a debug build installed next to it)
 * passes its own scheme to [uri] and [parse].
 *
 * - `nomaddashboard://dashboard` opens the dashboard.
 * - `nomaddashboard://dashboard/weather` opens the dashboard scrolled to, and expanding, the Weather card.
 * - `nomaddashboard://settings` opens Settings.
 * - `nomaddashboard://settings/homeTimeZone` opens the Settings page of that group, scrolled to it.
 *
 * Names are the lower camel case form of the [DashboardCardId] and [SettingsDestination] constants and
 * are matched ignoring case. Anything else, including a link for another scheme, is ignored.
 */
sealed interface NomadDeepLink {
    /** Opens the dashboard, scrolled to [card] when one is given. */
    data class Dashboard(val card: DashboardCardId? = null) : NomadDeepLink

    /** Opens Settings, on the page and group of [destination] when one is given. */
    data class Settings(val destination: SettingsDestination? = null) : NomadDeepLink

    /** The link as a URI string, ready for an intent filter, a shortcut intent or a widget tap. */
    fun uri(scheme: String = DEFAULT_SCHEME): String =
        when (this) {
            is Dashboard -> build(scheme, DASHBOARD_TARGET, card?.let(::cardName))
            is Settings -> build(scheme, SETTINGS_TARGET, destination?.let(::destinationName))
        }

    companion object {
        /** The URL scheme of the release app. */
        const val DEFAULT_SCHEME = "nomaddashboard"

        private const val DASHBOARD_TARGET = "dashboard"
        private const val SETTINGS_TARGET = "settings"

        private val cardsByName: Map<String, DashboardCardId> =
            DashboardCardId.entries.associateBy { cardName(it).lowercase(Locale.ROOT) }
        private val destinationsByName: Map<String, SettingsDestination> =
            SettingsDestination.entries.associateBy { destinationName(it).lowercase(Locale.ROOT) }

        /** Recognises a link addressed to [scheme]; anything else is ignored. */
        fun parse(uri: String, scheme: String = DEFAULT_SCHEME): NomadDeepLink? {
            val parsed = try {
                URI(uri)
            } catch (_: URISyntaxException) {
                return null
            }
            if (!parsed.scheme.equals(scheme, ignoreCase = true)) return null

            val names = parsed.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
            if (names.size > 1) return null
            val name = names.firstOrNull()?.lowercase(Locale.ROOT)

            return when (parsed.host?.lowercase(Locale.ROOT)) {
                DASHBOARD_TARGET -> {
                    if (name == null) Dashboard() else cardsByName[name]?.let(::Dashboard)
                }

                SETTINGS_TARGET -> {
                    if (name == null) Settings() else destinationsByName[name]?.let(::Settings)
                }

                else -> null
            }
        }

        private fun build(scheme: String, target: String, name: String?): String =
            "$scheme://$target" + if (name != null) "/$name" else ""

        private fun cardName(card: DashboardCardId): String = lowerCamelCase(card.name)

        private fun destinationName(destination: SettingsDestination): String = lowerCamelCase(destination.name)

        /** "HOME_TIME_ZONE" becomes "homeTimeZone". */
        private fun lowerCamelCase(constantName: String): String =
            constantName.lowercase(Locale.ROOT).split('_').mapIndexed { index, word ->
                if (index == 0) word else word.replaceFirstChar { it.uppercase() }
            }.joinToString("")
    }
}
