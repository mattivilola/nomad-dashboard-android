package com.iloapps.nomaddashboard.feature.widgets

import com.iloapps.nomaddashboard.core.model.DashboardCardId
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.travel.answers.NomadDeepLink
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.widget.DaylightWidgetContent
import com.iloapps.nomaddashboard.core.travel.widget.SafetyWidgetContent

/**
 * Where a tap on a widget opens the app: the dashboard scrolled to the card the tapped content comes
 * from, or the Settings group that fixes what the widget is missing. The same links the launcher
 * shortcuts use.
 */
internal object WidgetLinks {
    /** Anywhere on a widget that has no more specific target, and the widgets before the app has run. */
    val dashboard: NomadDeepLink = NomadDeepLink.Dashboard()

    /** A heads-up row opens the card the dashboard would scroll to for the same item. */
    fun headsUp(kind: HeadsUpKind?): NomadDeepLink =
        kind?.let { NomadDeepLink.Dashboard(card(it)) } ?: dashboard

    /** The emergency number comes from the Emergency Care card's subtitle and the Local Info card. */
    val emergency: NomadDeepLink = NomadDeepLink.Dashboard(DashboardCardId.EMERGENCY_CARE)

    /** The consular line opens Your Embassy; without a citizenship it opens the setting that unlocks it. */
    fun consular(content: SafetyWidgetContent): NomadDeepLink =
        if (content.citizenshipHint != null) {
            NomadDeepLink.Settings(SettingsDestination.CITIZENSHIP)
        } else {
            NomadDeepLink.Dashboard(DashboardCardId.EMBASSY)
        }

    /** The sunrise and sunset line lives on the Weather card; without a location there is nothing to show yet. */
    fun daylight(content: DaylightWidgetContent): NomadDeepLink =
        if (content.kind == DaylightWidgetContent.Kind.LOCATION_NEEDED) {
            dashboard
        } else {
            NomadDeepLink.Dashboard(DashboardCardId.WEATHER)
        }

    /**
     * The card a heads-up of [kind] expands on the dashboard. The widget snapshot keeps only the kind, so
     * this repeats the engine's choice of card.
     */
    fun card(kind: HeadsUpKind): DashboardCardId =
        when (kind) {
            HeadsUpKind.DATA_CAP -> DashboardCardId.CONNECTIVITY
            HeadsUpKind.STAY_ALLOWANCE,
            HeadsUpKind.SCHENGEN_LIMIT,
            HeadsUpKind.TAX_RESIDENCE_DAYS,
            -> DashboardCardId.STAYS

            HeadsUpKind.TRAVEL_ADVISORY,
            HeadsUpKind.WEATHER_ALERT,
            HeadsUpKind.REGIONAL_SECURITY,
            -> DashboardCardId.TRAVEL_ALERTS

            HeadsUpKind.STRONG_WIND,
            HeadsUpKind.RAIN_SOON,
            HeadsUpKind.UV_INDEX,
            HeadsUpKind.AIR_QUALITY,
            HeadsUpKind.DAYLIGHT,
            -> DashboardCardId.WEATHER

            HeadsUpKind.PUBLIC_HOLIDAY,
            HeadsUpKind.LOCAL_RHYTHM,
            -> DashboardCardId.LOCAL_INFO

            HeadsUpKind.FUEL_NEARBY -> DashboardCardId.FUEL_PRICES
        }
}
