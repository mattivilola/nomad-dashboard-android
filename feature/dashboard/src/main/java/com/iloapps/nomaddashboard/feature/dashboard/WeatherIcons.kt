package com.iloapps.nomaddashboard.feature.dashboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The symbol for a WMO weather code, shared by the weather tile and the Weather card. Named apart from
 * the private `weatherIconFor` in WeatherCard.kt, which the Weather card drops in favour of this.
 */
internal fun weatherConditionIcon(code: Int?): ImageVector =
    when (code) {
        0 -> Icons.Rounded.WbSunny
        1, 2, 3 -> Icons.Rounded.Cloud
        45, 48 -> Icons.Rounded.BlurOn
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Rounded.WaterDrop
        71, 73, 75 -> Icons.Rounded.AcUnit
        95, 96, 99 -> Icons.Rounded.Thunderstorm
        else -> Icons.Rounded.Cloud
    }
