package com.iloapps.nomaddashboard.feature.widgets

import androidx.annotation.DrawableRes
import com.iloapps.nomaddashboard.core.travel.headsup.HeadsUpKind
import com.iloapps.nomaddashboard.core.travel.health.HealthSymbol
import com.iloapps.nomaddashboard.core.travel.widget.DaylightWidgetContent

/**
 * The widget icons: vector drawables of the same Material icons the dashboard draws for each symbol
 * (see `DashboardGlance.kt`), because a Glance widget cannot draw a Compose `ImageVector`.
 */
internal object WidgetIcons {
    @DrawableRes
    fun verdict(symbol: HealthSymbol): Int =
        when (symbol) {
            HealthSymbol.OVERALL_READY -> R.drawable.ic_widget_verdict_ready
            HealthSymbol.OVERALL_CAUTION -> R.drawable.ic_widget_verdict_caution
            HealthSymbol.OVERALL_ATTENTION -> R.drawable.ic_widget_verdict_attention
            else -> R.drawable.ic_widget_verdict_waiting
        }

    /** A kind this build does not know (a snapshot from a newer build) gets the generic event icon. */
    @DrawableRes
    fun headsUp(kind: HeadsUpKind?): Int =
        when (kind) {
            HeadsUpKind.DATA_CAP -> R.drawable.ic_widget_data_usage
            HeadsUpKind.STAY_ALLOWANCE, HeadsUpKind.SCHENGEN_LIMIT -> R.drawable.ic_widget_event_busy
            HeadsUpKind.TAX_RESIDENCE_DAYS -> R.drawable.ic_widget_receipt_long
            HeadsUpKind.TRAVEL_ADVISORY -> R.drawable.ic_widget_shield
            HeadsUpKind.WEATHER_ALERT -> R.drawable.ic_widget_thunderstorm
            HeadsUpKind.REGIONAL_SECURITY -> R.drawable.ic_widget_security
            HeadsUpKind.STRONG_WIND, HeadsUpKind.AIR_QUALITY -> R.drawable.ic_widget_air
            HeadsUpKind.RAIN_SOON -> R.drawable.ic_widget_water_drop
            HeadsUpKind.UV_INDEX -> R.drawable.ic_widget_light_mode
            HeadsUpKind.PUBLIC_HOLIDAY, null -> R.drawable.ic_widget_event
            HeadsUpKind.LOCAL_RHYTHM -> R.drawable.ic_widget_event_repeat
            HeadsUpKind.FUEL_NEARBY -> R.drawable.ic_widget_local_gas_station
            HeadsUpKind.DAYLIGHT -> R.drawable.ic_widget_wb_twilight
        }

    @DrawableRes
    fun daylight(kind: DaylightWidgetContent.Kind): Int =
        when (kind) {
            DaylightWidgetContent.Kind.DAYLIGHT -> R.drawable.ic_widget_wb_twilight
            DaylightWidgetContent.Kind.DARK_UNTIL,
            DaylightWidgetContent.Kind.DARK,
            DaylightWidgetContent.Kind.POLAR_NIGHT,
            -> R.drawable.ic_widget_bedtime

            DaylightWidgetContent.Kind.MIDNIGHT_SUN -> R.drawable.ic_widget_light_mode
            DaylightWidgetContent.Kind.LOCATION_NEEDED -> R.drawable.ic_widget_location_off
            DaylightWidgetContent.Kind.OUT_OF_DATE -> R.drawable.ic_widget_schedule
        }
}
