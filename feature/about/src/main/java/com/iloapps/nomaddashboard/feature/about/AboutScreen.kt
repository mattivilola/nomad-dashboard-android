package com.iloapps.nomaddashboard.feature.about

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Luggage
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iloapps.nomaddashboard.core.designsystem.component.NomadActionChip
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadMetricBlock
import com.iloapps.nomaddashboard.core.designsystem.component.NomadPill
import com.iloapps.nomaddashboard.core.designsystem.component.NomadSectionClusterHeader
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val appMetadata = remember(context) { resolveAppMetadata(context) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            NomadCard {
                NomadTopBar(
                    title = "About",
                    subtitle = "Nomad Dashboard for Android",
                    supportingText = "A travel control room for nomads and frequent travelers who need connection, weather, movement, safety, and local tools in one calm surface.",
                    badgeText = "Built for life in motion",
                    badgeTone = NomadBadgeTone.Accent,
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Luggage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    },
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AboutLinkAction(
                        label = "Website",
                        icon = Icons.Rounded.Language,
                        onClick = { uriHandler.openUri("https://nomaddashboard.com") },
                    )
                    AboutLinkAction(
                        label = "Project site",
                        icon = Icons.Rounded.Code,
                        onClick = { uriHandler.openUri("https://github.com/mattivilola/nomad-dashboard-android") },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    NomadMetricBlock(
                        label = "Platform",
                        value = "Android",
                        supportingText = "adaptive phone-first travel utility",
                        modifier = Modifier.weight(1f),
                    )
                    NomadMetricBlock(
                        label = "Data",
                        value = "Local",
                        supportingText = "settings and credentials stay on-device",
                        modifier = Modifier.weight(1f),
                    )
                }
                NomadPill(text = appMetadata.versionLabel)
            }
        }

        item {
            NomadCard {
                NomadSectionClusterHeader(
                    title = "Why It Feels Different",
                    subtitle = "The goal is not more chrome. The goal is faster orientation when you are changing cities, networks, or plans.",
                    badges = listOf(
                        "Fast scan" to NomadBadgeTone.Good,
                        "Nomad-first" to NomadBadgeTone.Info,
                    ),
                )
                AboutCallout(
                    icon = Icons.Rounded.TravelExplore,
                    title = "See your current operating context immediately",
                    description = "Weather, travel context, alerts, nearby essentials, movement history, and time tracking are meant to stay legible without tab hopping.",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                AboutCallout(
                    icon = Icons.Rounded.Route,
                    title = "Designed for movement instead of dashboard theater",
                    description = "Hierarchy, density, and action placement matter more than decorative cards. The app should answer what changed and what needs action in seconds.",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                AboutCallout(
                    icon = Icons.Rounded.Shield,
                    title = "Useful even when trust is limited",
                    description = "Local-first behavior matters when you are on hotel Wi-Fi, crossing borders, or deciding whether a work tool belongs on a personal phone.",
                )
            }
        }

        item {
            NomadCard {
                NomadSectionClusterHeader(
                    title = "Ownership, Privacy, And Source",
                    subtitle = "The app should be easy to trust before you ever enter a credential.",
                    badges = listOf(
                        "Encrypted" to NomadBadgeTone.Good,
                        "Open source" to NomadBadgeTone.Accent,
                    ),
                )
                AboutFactRow(
                    icon = Icons.Rounded.Lock,
                    title = "Credentials are entered after install",
                    description = "User-managed provider keys are stored only in encrypted device storage backed by Android Keystore.",
                )
                AboutFactRow(
                    icon = Icons.Rounded.Shield,
                    title = "No bundled provider secrets",
                    description = "The app does not ship user credentials in resources, BuildConfig, or manifest placeholders.",
                )
                AboutFactRow(
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    title = "Clear source and destination",
                    description = "The public website and GitHub project are linked here so the About screen can act like a real trust surface, not filler text.",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Made with",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "by Matti Vilola. Distributed by ILO APPLICATIONS SL.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutLinkAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    NomadActionChip(
        label = label,
        icon = icon,
        onClick = onClick,
    )
}

@Composable
private fun AboutCallout(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.74f),
            )
        }
    }
}

@Composable
private fun AboutFactRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
            )
        }
    }
}

private data class AppMetadata(
    val appName: String,
    val versionName: String,
    val versionCode: Long,
) {
    val versionLabel: String
        get() = "$appName $versionName ($versionCode)"
}

private fun resolveAppMetadata(context: Context): AppMetadata {
    val packageManager = context.packageManager
    val packageInfo = packageManager.packageInfoFor(context.packageName)
    val appName = packageManager.getApplicationLabel(context.applicationInfo).toString()

    return AppMetadata(
        appName = appName,
        versionName = packageInfo.versionName.orEmpty(),
        versionCode = packageInfo.longVersionCode,
    )
}

@Suppress("DEPRECATION")
private fun PackageManager.packageInfoFor(packageName: String) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        getPackageInfo(packageName, 0)
    }
