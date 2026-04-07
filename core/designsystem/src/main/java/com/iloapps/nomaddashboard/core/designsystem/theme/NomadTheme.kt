package com.iloapps.nomaddashboard.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

val Sand = Color(0xFFF4EFE3)
val Sea = Color(0xFF5AA8A8)
val Coral = Color(0xFFDD7C62)
val Ink = Color(0xFF1F2A37)
val Cloud = Color(0xFFE5EDF0)
val Card = Color(0xFFFBF9F3)
val Border = Color(0xFFD9E2E6)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Sea,
    secondary = Coral,
    background = Sand,
    surface = Card,
    surfaceVariant = Cloud,
    tertiary = Color(0xFFB08838),
    surfaceTint = Sea,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    outline = Border,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF8ED0CD),
    secondary = Color(0xFFFFA58B),
    background = Color(0xFF152028),
    surface = Color(0xFF1E2B34),
    surfaceVariant = Color(0xFF203741),
    tertiary = Color(0xFFE1C16D),
    surfaceTint = Color(0xFF8ED0CD),
    onPrimary = Ink,
    onSecondary = Ink,
    onBackground = Color(0xFFF3F4F6),
    onSurface = Color(0xFFF3F4F6),
    outline = Color(0xFF43606B),
)

private val NomadTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 28.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 30.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
)

private val NomadShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun NomadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NomadTypography,
        shapes = NomadShapes,
        content = content,
    )
}
