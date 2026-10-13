package com.iloapps.nomaddashboard.feature.settings

import com.google.common.truth.Truth.assertThat
import com.iloapps.nomaddashboard.core.data.companion.CompanionRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.model.AppSettings
import com.iloapps.nomaddashboard.core.model.CompanionImportPreview
import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.model.NomadCompanionData
import com.iloapps.nomaddashboard.core.model.OfflineEssential
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import com.iloapps.nomaddashboard.core.model.SettingsDestination
import com.iloapps.nomaddashboard.core.model.SettingsPage
import com.iloapps.nomaddashboard.core.model.SurfSpotConfiguration
import com.iloapps.nomaddashboard.core.model.TimezonePlanner
import com.iloapps.nomaddashboard.core.model.VisitedCountryDay
import com.iloapps.nomaddashboard.core.model.VisitedPlace
import com.iloapps.nomaddashboard.core.model.VisitedPlaceEvent
import com.iloapps.nomaddashboard.core.model.WorkplaceVisit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dashboard = FakeDashboardRepository()
    private val companion = FakeCompanionRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = SettingsViewModel(dashboard, companion)

    /** The state flow only runs while it has a subscriber, as it does on screen. */
    private fun SettingsViewModel.collecting(block: SettingsViewModel.() -> Unit) = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { uiState.collect {} }
        block()
    }

    @Test
    fun it_starts_on_the_page_list() = viewModel().collecting {
        assertThat(uiState.value.navigation.selectedPage).isNull()
    }

    @Test
    fun a_deep_link_selects_the_page_and_focuses_the_group() = viewModel().collecting {
        applyDeepLink(SettingsDestination.FUEL_PRICES)

        assertThat(uiState.value.navigation.selectedPage).isEqualTo(SettingsPage.WEATHER_AND_LOCAL)
        assertThat(uiState.value.navigation.focus).isEqualTo(SettingsDestination.FUEL_PRICES)
    }

    @Test
    fun a_deep_link_is_applied_once_per_screen_instance() = viewModel().collecting {
        applyDeepLink(SettingsDestination.WEATHER)
        focusHandled()
        openPage(SettingsPage.WORK)
        applyDeepLink(SettingsDestination.WEATHER)

        assertThat(uiState.value.navigation.selectedPage).isEqualTo(SettingsPage.WORK)
        assertThat(uiState.value.navigation.focus).isNull()
    }

    @Test
    fun a_missing_deep_link_changes_nothing() = viewModel().collecting {
        applyDeepLink(null)

        assertThat(uiState.value.navigation).isEqualTo(SettingsNavigationState())
    }

    @Test
    fun opening_a_page_and_going_back_follows_the_navigation_state() = viewModel().collecting {
        openPage(SettingsPage.SAFETY)
        assertThat(uiState.value.navigation.selectedPage).isEqualTo(SettingsPage.SAFETY)

        back()
        assertThat(uiState.value.navigation.selectedPage).isNull()
    }

    @Test
    fun citizenship_is_limited_to_three_countries_when_added_through_settings() = viewModel().collecting {
        listOf("FI", "SE", "NO", "DK").forEach { code ->
            update { SettingsEditing.addCitizenship(it, code) }
        }

        assertThat(uiState.value.settings.citizenshipCountryCodes).containsExactly("FI", "SE", "NO").inOrder()

        update { SettingsEditing.removeCitizenship(it, "SE") }
        update { SettingsEditing.addCitizenship(it, "DK") }

        assertThat(uiState.value.settings.citizenshipCountryCodes).containsExactly("FI", "NO", "DK").inOrder()
    }

    @Test
    fun every_settings_change_refreshes_the_dashboard() = viewModel().collecting {
        update { it.copy(statusShowsHeadsUp = true) }

        assertThat(dashboard.settings.value.statusShowsHeadsUp).isTrue()
        assertThat(dashboard.refreshCount).isEqualTo(1)
    }

    @Test
    fun companion_preferences_are_read_and_written_through_the_companion_repository() = viewModel().collecting {
        updateCompanionPreferences { it.copy(homeTimeZoneId = "Europe/Helsinki", quietNotificationsEnabled = true) }

        assertThat(uiState.value.companionPreferences.homeTimeZoneId).isEqualTo("Europe/Helsinki")
        assertThat(uiState.value.companionPreferences.quietNotificationsEnabled).isTrue()
        assertThat(companion.data.value.preferences.homeTimeZoneId).isEqualTo("Europe/Helsinki")
    }

    @Test
    fun a_failed_companion_write_is_reported_instead_of_crashing() = viewModel().collecting {
        companion.failure = IllegalStateException("Companion data could not be read; the existing file was kept.")

        updateCompanionPreferences { it.copy(workplaceDiaryEnabled = true) }

        assertThat(uiState.value.companionError).isEqualTo("Companion data could not be read; the existing file was kept.")
        assertThat(uiState.value.companionPreferences.workplaceDiaryEnabled).isFalse()
    }

    @Test
    fun provider_credentials_are_updated_through_the_dashboard_repository() = viewModel().collecting {
        updateProviderCredentials { it.copy(reliefWebAppName = "nomad-app") }

        assertThat(uiState.value.providerCredentials.reliefWebAppName).isEqualTo("nomad-app")
    }
}

private class FakeDashboardRepository : NomadDashboardRepository {
    override val settings = MutableStateFlow(AppSettings())
    override val providerCredentials = MutableStateFlow(ProviderCredentialSettings())
    override val snapshot: StateFlow<DashboardSnapshot> = MutableStateFlow(DashboardSnapshot())
    override val visitedPlaces: Flow<List<VisitedPlace>> = emptyFlow()
    override val visitedCountryDays: Flow<List<VisitedCountryDay>> = emptyFlow()
    override val visitedPlaceEvents: Flow<List<VisitedPlaceEvent>> = emptyFlow()
    var refreshCount = 0

    override suspend fun warmStart() = Unit
    override suspend fun refresh() {
        refreshCount += 1
    }

    override suspend fun clearVisitedHistory() = Unit
    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settings.update(transform)
    }

    override suspend fun updateProviderCredentials(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings) {
        providerCredentials.update(transform)
    }

    override suspend fun resolveSurfSpotFromCurrentLocation(): SurfSpotConfiguration? = null
}

private class FakeCompanionRepository : CompanionRepository {
    override val data = MutableStateFlow(NomadCompanionData())
    override val storageError = MutableStateFlow<String?>(null)
    var failure: Throwable? = null

    override suspend fun update(transform: (NomadCompanionData) -> NomadCompanionData) {
        failure?.let { throw it }
        data.update(transform)
    }

    override suspend fun saveEssential(item: OfflineEssential) = Unit
    override suspend fun savePlanner(planner: TimezonePlanner) = Unit
    override suspend fun saveVisit(visit: WorkplaceVisit) = Unit
    override suspend fun exportBackup(): String = ""
    override suspend fun previewImport(serialized: String) = CompanionImportPreview(accepted = false, message = "")
    override suspend fun mergeImport(serialized: String) = CompanionImportPreview(accepted = false, message = "")
}
