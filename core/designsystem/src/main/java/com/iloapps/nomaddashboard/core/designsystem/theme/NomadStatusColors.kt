package com.iloapps.nomaddashboard.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * One status colour. Colour means status only: teal is good, sand (amber) is caution, coral is
 * attention, anything else is neutral. Never colour a value by its metric or category.
 *
 * @property fill Colour for non-text marks: bars, sparklines, dots and tinted backgrounds.
 * @property text Colour for text and icons drawn on a [tint] of [fill]. It is darker than [fill] in the
 * light theme so a tinted badge keeps a readable contrast.
 */
@Immutable
class NomadStatusColor(
    val fill: Color,
    val text: Color,
) {
    /** A translucent wash of [fill] for a badge or button background. */
    fun tint(alpha: Float = DefaultTintAlpha): Color = fill.copy(alpha = alpha)

    companion object {
        const val DefaultTintAlpha = 0.16f
    }
}

/** The status colour tokens for the current theme. Read them through [nomadStatusColors]. */
@Immutable
class NomadStatusColors(
    val good: NomadStatusColor,
    val caution: NomadStatusColor,
    val attention: NomadStatusColor,
    val neutral: NomadStatusColor,
)

/** Sand/amber from the theme's tertiary colour, with a darker shade for text on the light theme. */
private val LightCautionFill = Color(0xFFB08838)
private val LightCautionText = Color(0xFF7A5A12)
private val LightGoodText = Color(0xFF1F6B6B)
private val LightAttentionText = Color(0xFFAE3F26)

private val DarkGood = Color(0xFF8ED0CD)
private val DarkCaution = Color(0xFFE1C16D)
private val DarkAttention = Color(0xFFFFA58B)

/**
 * The status colours that match the active [MaterialTheme]: the same palette as the theme (Sea, tertiary
 * sand, Coral) with text shades that stay readable on their tint in both the light and the dark theme.
 */
@Composable
fun nomadStatusColors(): NomadStatusColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.background.luminance() < 0.5f
    val onSurface = scheme.onSurface
    return remember(isDark, onSurface) { nomadStatusColors(isDark, onSurface) }
}

/** The same tokens for a surface without a [MaterialTheme], given the theme and its text colour. */
fun nomadStatusColors(isDark: Boolean, onSurface: Color): NomadStatusColors =
    if (isDark) {
        NomadStatusColors(
            good = NomadStatusColor(fill = DarkGood, text = DarkGood),
            caution = NomadStatusColor(fill = DarkCaution, text = DarkCaution),
            attention = NomadStatusColor(fill = DarkAttention, text = DarkAttention),
            neutral = NomadStatusColor(fill = onSurface.copy(alpha = 0.38f), text = onSurface.copy(alpha = 0.72f)),
        )
    } else {
        NomadStatusColors(
            good = NomadStatusColor(fill = Sea, text = LightGoodText),
            caution = NomadStatusColor(fill = LightCautionFill, text = LightCautionText),
            attention = NomadStatusColor(fill = Coral, text = LightAttentionText),
            neutral = NomadStatusColor(fill = onSurface.copy(alpha = 0.38f), text = onSurface.copy(alpha = 0.72f)),
        )
    }
