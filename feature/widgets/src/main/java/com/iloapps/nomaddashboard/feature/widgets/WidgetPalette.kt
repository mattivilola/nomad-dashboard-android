package com.iloapps.nomaddashboard.feature.widgets

import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadStatusColor
import com.iloapps.nomaddashboard.core.designsystem.theme.NomadStatusColors
import com.iloapps.nomaddashboard.core.designsystem.theme.nomadColorScheme
import com.iloapps.nomaddashboard.core.designsystem.theme.nomadStatusColors
import com.iloapps.nomaddashboard.core.travel.health.HealthLevel

/**
 * The widget colours: the Nomad theme tokens from `core:designsystem`, as day and night colour providers
 * so the launcher switches them with the system theme. Colour means status only: teal is good, sand
 * caution, coral attention and everything else is plain text.
 */
internal object WidgetPalette {
    private val light = nomadColorScheme(darkTheme = false)
    private val dark = nomadColorScheme(darkTheme = true)
    private val lightStatus = nomadStatusColors(isDark = false, onSurface = light.onSurface)
    private val darkStatus = nomadStatusColors(isDark = true, onSurface = dark.onSurface)

    val background = ColorProvider(day = light.background, night = dark.background)
    val primaryText = ColorProvider(day = light.onSurface, night = dark.onSurface)
    val secondaryText = text(alpha = SecondaryAlpha)
    val tertiaryText = text(alpha = TertiaryAlpha)

    /** Content that is out of date is drawn quieter, like the macOS widgets' 60% opacity. */
    val dimmedText = text(alpha = DimmedAlpha)

    val good = status(NomadStatusColors::good)
    val caution = status(NomadStatusColors::caution)
    val attention = status(NomadStatusColors::attention)

    /** The status colour of a health level; an unavailable reading is neutral. */
    fun of(level: HealthLevel): ColorProvider =
        when (level) {
            HealthLevel.READY -> good
            HealthLevel.CAUTION -> caution
            HealthLevel.ATTENTION -> attention
            HealthLevel.UNAVAILABLE -> secondaryText
        }

    private fun text(alpha: Float) = ColorProvider(
        day = light.onSurface.copy(alpha = alpha),
        night = dark.onSurface.copy(alpha = alpha),
    )

    private fun status(pick: (NomadStatusColors) -> NomadStatusColor) = ColorProvider(
        day = pick(lightStatus).text,
        night = pick(darkStatus).text,
    )

    private const val SecondaryAlpha = 0.68f
    private const val TertiaryAlpha = 0.5f
    private const val DimmedAlpha = 0.6f
}
