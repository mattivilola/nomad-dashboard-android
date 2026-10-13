package com.iloapps.nomaddashboard.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iloapps.nomaddashboard.core.designsystem.component.NomadBadgeTone
import com.iloapps.nomaddashboard.core.designsystem.component.NomadCard
import com.iloapps.nomaddashboard.core.designsystem.component.NomadTopBar
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.SettingsPage
import kotlinx.coroutines.delay

internal const val SettingsPageListTag = "settings_page_list"
internal const val SettingsPageDetailTag = "settings_page_detail"
internal const val SettingsBackButtonTag = "settings_back"

internal fun settingsPageTag(page: SettingsPage): String = "settings_page_${page.name}"

private const val HighlightMillis = 1_800L

/**
 * Settings as a hoisted-state screen host. [destination] is the optional deep link a navigation route
 * carried; [isWideLayout] shows the page list and the open page side by side.
 */
@Composable
fun SettingsRoute(
    modifier: Modifier = Modifier,
    destination: SettingsDestination? = null,
    isWideLayout: Boolean = false,
    onOpenCompanion: () -> Unit = {},
    onOpenVisitedMap: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(destination) { viewModel.applyDeepLink(destination) }
    val actions = remember(viewModel, onOpenCompanion, onOpenVisitedMap, onOpenAbout) {
        SettingsActions(
            onOpenPage = viewModel::openPage,
            onBack = viewModel::back,
            onOpenDestination = viewModel::openDestination,
            onFocusHandled = viewModel::focusHandled,
            onUpdate = viewModel::update,
            onUpdateProviderCredentials = viewModel::updateProviderCredentials,
            onUpdateCompanionPreferences = viewModel::updateCompanionPreferences,
            onFillSurfSpotFromCurrentLocation = viewModel::fillSurfSpotFromCurrentLocation,
            onRefresh = viewModel::refresh,
            onOpenCompanion = onOpenCompanion,
            onOpenVisitedMap = onOpenVisitedMap,
            onOpenAbout = onOpenAbout,
        )
    }
    SettingsScreen(
        uiState = uiState,
        actions = actions,
        isWideLayout = isWideLayout,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsActions,
    isWideLayout: Boolean,
    modifier: Modifier = Modifier,
) {
    val page = uiState.navigation.effectivePage(isWideLayout)
    if (isWideLayout) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsPageList(
                selected = page,
                onOpenPage = actions.onOpenPage,
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
            )
            key(page) {
                SettingsPageDetail(
                    page = page ?: SettingsPage.entries.first(),
                    uiState = uiState,
                    actions = actions,
                    showBack = false,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    } else if (page == null) {
        SettingsPageList(selected = null, onOpenPage = actions.onOpenPage, modifier = modifier.fillMaxSize())
    } else {
        BackHandler(onBack = actions.onBack)
        key(page) {
            SettingsPageDetail(
                page = page,
                uiState = uiState,
                actions = actions,
                showBack = true,
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsPageList(
    selected: SettingsPage?,
    onOpenPage: (SettingsPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag(SettingsPageListTag),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NomadCard {
                NomadTopBar(
                    title = "Settings",
                    subtitle = "Control room",
                    supportingText = "Pages ordered the way a traveller thinks. Everything stays local-first.",
                    badgeText = "Device local only",
                    badgeTone = NomadBadgeTone.Good,
                    trailing = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                )
            }
        }
        items(SettingsPage.entries, key = { it.name }) { page ->
            SettingsPageRow(page = page, isSelected = page == selected, onClick = { onOpenPage(page) })
        }
    }
}

@Composable
private fun SettingsPageRow(page: SettingsPage, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    NomadCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                selected = isSelected
            }
            .testTag(settingsPageTag(page)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(page.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    page.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SettingsPageDetail(
    page: SettingsPage,
    uiState: SettingsUiState,
    actions: SettingsActions,
    showBack: Boolean,
    modifier: Modifier = Modifier,
) {
    val groups = remember(page) { SettingsGroup.groupsOn(page) }
    val listState = rememberLazyListState()
    var highlighted by remember { mutableStateOf<SettingsGroup?>(null) }
    val navigation = uiState.navigation

    LaunchedEffect(page, navigation.focusRequest) {
        val destination = navigation.focus?.takeIf { it.page == page } ?: return@LaunchedEffect
        val group = SettingsGroup.forDestination(destination)
        listState.animateScrollToItem(index = 1 + groups.indexOf(group))
        actions.onFocusHandled()
        highlighted = group
        delay(HighlightMillis)
        highlighted = null
    }

    val scope = SettingsGroupScope(state = uiState, actions = actions, highlighted = highlighted)
    LazyColumn(
        modifier = modifier.testTag(SettingsPageDetailTag),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            NomadCard {
                NomadTopBar(
                    title = page.title,
                    subtitle = "Settings",
                    supportingText = page.description,
                    titleLeading = if (showBack) {
                        {
                            IconButton(
                                onClick = actions.onBack,
                                modifier = Modifier.testTag(SettingsBackButtonTag),
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to settings")
                            }
                        }
                    } else {
                        null
                    },
                )
            }
        }
        items(groups, key = { it.name }) { group ->
            SettingsGroupContent(group, scope)
        }
    }
}

@Composable
private fun SettingsGroupContent(group: SettingsGroup, scope: SettingsGroupScope) {
    when (group.page) {
        SettingsPage.GENERAL -> GeneralGroup(group, scope)
        SettingsPage.WEATHER_AND_LOCAL -> WeatherLocalGroup(group, scope)
        SettingsPage.SAFETY -> SafetyGroup(group, scope)
        SettingsPage.STAYS_AND_VISAS -> StaysGroup(group, scope)
        SettingsPage.CONNECTION_AND_DATA -> ConnectionGroup(group, scope)
        SettingsPage.WORK -> WorkGroup(group, scope)
        SettingsPage.PRIVACY_AND_LOCATION -> PrivacyGroup(group, scope)
        SettingsPage.ADVANCED -> AdvancedGroup(group, scope)
    }
}

private val SettingsPage.icon: ImageVector
    get() = when (this) {
        SettingsPage.GENERAL -> Icons.Rounded.Settings
        SettingsPage.WEATHER_AND_LOCAL -> Icons.Rounded.Cloud
        SettingsPage.SAFETY -> Icons.Rounded.HealthAndSafety
        SettingsPage.STAYS_AND_VISAS -> Icons.Rounded.CalendarMonth
        SettingsPage.CONNECTION_AND_DATA -> Icons.Rounded.SignalCellularAlt
        SettingsPage.WORK -> Icons.Rounded.Work
        SettingsPage.PRIVACY_AND_LOCATION -> Icons.Rounded.Lock
        SettingsPage.ADVANCED -> Icons.Rounded.Tune
    }

internal val SettingsPage.description: String
    get() = when (this) {
        SettingsPage.GENERAL -> "Travel profile, home, citizenship, appearance and dashboard layout."
        SettingsPage.WEATHER_AND_LOCAL -> "Weather location, air quality, surf spot, Local Info and fuel prices."
        SettingsPage.SAFETY -> "Travel alerts, emergency care and your embassy."
        SettingsPage.STAYS_AND_VISAS -> "Schengen counter, stay allowances and tax residence days."
        SettingsPage.CONNECTION_AND_DATA -> "Data usage, offline essentials and quiet connection alerts."
        SettingsPage.WORK -> "Project time tracking and the workplace diary."
        SettingsPage.PRIVACY_AND_LOCATION -> "Location access, IP lookups, visited places and analytics."
        SettingsPage.ADVANCED -> "Refresh and About."
    }
