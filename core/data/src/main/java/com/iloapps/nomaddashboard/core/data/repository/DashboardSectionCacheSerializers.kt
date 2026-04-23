package com.iloapps.nomaddashboard.core.data.repository

import com.iloapps.nomaddashboard.core.database.entity.DashboardSectionCacheEntity
import com.iloapps.nomaddashboard.core.model.EmergencyCareFacility
import com.iloapps.nomaddashboard.core.model.EmergencyCareLocationSource
import com.iloapps.nomaddashboard.core.model.EmergencyCareSnapshot
import com.iloapps.nomaddashboard.core.model.EmergencyCareStatus
import com.iloapps.nomaddashboard.core.model.FuelPriceSnapshot
import com.iloapps.nomaddashboard.core.model.FuelPriceStatus
import com.iloapps.nomaddashboard.core.model.FuelStationPrice
import com.iloapps.nomaddashboard.core.model.FuelType
import com.iloapps.nomaddashboard.core.model.HolidayPeriod
import com.iloapps.nomaddashboard.core.model.HolidaySourceAttribution
import com.iloapps.nomaddashboard.core.model.LocalHolidayPhase
import com.iloapps.nomaddashboard.core.model.LocalHolidayStatus
import com.iloapps.nomaddashboard.core.model.LocalInfoSnapshot
import com.iloapps.nomaddashboard.core.model.LocalInfoStatus
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorKind
import com.iloapps.nomaddashboard.core.model.LocalPriceIndicatorRow
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelSnapshot
import com.iloapps.nomaddashboard.core.model.LocalPriceLevelStatus
import com.iloapps.nomaddashboard.core.model.LocalPricePrecision
import com.iloapps.nomaddashboard.core.model.LocalPriceSummaryBand
import com.iloapps.nomaddashboard.core.model.TravelAlertKind
import com.iloapps.nomaddashboard.core.model.TravelAlertsSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertSeverity
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalSnapshot
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalState
import com.iloapps.nomaddashboard.core.model.TravelAlertSignalStatus
import com.iloapps.nomaddashboard.core.model.TravelAlertUnavailableReason
import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot
import com.iloapps.nomaddashboard.core.model.WeatherDayForecast
import com.iloapps.nomaddashboard.core.model.WeatherHourlyForecastSlot
import com.iloapps.nomaddashboard.core.model.WeatherSnapshot
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class CachedDashboardSections(
    val travelContext: TravelContextSnapshot? = null,
    val weather: WeatherSnapshot? = null,
    val travelAlerts: TravelAlertsSnapshot? = null,
    val localInfo: LocalInfoSnapshot? = null,
    val fuelPrices: FuelPriceSnapshot? = null,
    val emergencyCare: EmergencyCareSnapshot? = null,
    val lastRefresh: Instant? = null,
)

internal fun loadCachedDashboardSections(
    entries: List<DashboardSectionCacheEntity>,
    json: Json,
): CachedDashboardSections {
    var travelContext: TravelContextSnapshot? = null
    var weather: WeatherSnapshot? = null
    var travelAlerts: TravelAlertsSnapshot? = null
    var localInfo: LocalInfoSnapshot? = null
    var fuelPrices: FuelPriceSnapshot? = null
    var emergencyCare: EmergencyCareSnapshot? = null
    var lastRefresh: Instant? = null

    entries.forEach { entry ->
        val fetchedAt = Instant.ofEpochMilli(entry.fetchedAtEpochMillis)
        if (lastRefresh == null || fetchedAt.isAfter(lastRefresh)) {
            lastRefresh = fetchedAt
        }
        when (entry.sectionId) {
            DashboardSectionId.TRAVEL_CONTEXT ->
                json.decodeOrNull<TravelContextCacheDto>(entry.payloadJson)?.toModel()?.let { travelContext = it }
            DashboardSectionId.WEATHER ->
                json.decodeOrNull<WeatherCacheDto>(entry.payloadJson)?.toModel()?.let { weather = it }
            DashboardSectionId.TRAVEL_ALERTS ->
                json.decodeOrNull<TravelAlertsCacheDto>(entry.payloadJson)?.toModel()?.let { travelAlerts = it }
            DashboardSectionId.LOCAL_INFO ->
                json.decodeOrNull<LocalInfoCacheDto>(entry.payloadJson)?.toModel()?.let { localInfo = it }
            DashboardSectionId.FUEL_PRICES ->
                json.decodeOrNull<FuelPriceCacheDto>(entry.payloadJson)?.toModel()?.let { fuelPrices = it }
            DashboardSectionId.EMERGENCY_CARE ->
                json.decodeOrNull<EmergencyCareCacheDto>(entry.payloadJson)?.toModel()?.let { emergencyCare = it }
        }
    }

    return CachedDashboardSections(
        travelContext = travelContext,
        weather = weather,
        travelAlerts = travelAlerts,
        localInfo = localInfo,
        fuelPrices = fuelPrices,
        emergencyCare = emergencyCare,
        lastRefresh = lastRefresh,
    )
}

internal fun TravelContextSnapshot.toCacheEntity(
    json: Json,
    fetchedAt: Instant,
): DashboardSectionCacheEntity = DashboardSectionCacheEntity(
    sectionId = DashboardSectionId.TRAVEL_CONTEXT,
    payloadJson = json.encodeToString(TravelContextCacheDto.fromModel(this)),
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
)

internal fun WeatherSnapshot.toCacheEntity(json: Json): DashboardSectionCacheEntity? {
    val fetchedAt = fetchedAt ?: return null
    return DashboardSectionCacheEntity(
        sectionId = DashboardSectionId.WEATHER,
        payloadJson = json.encodeToString(WeatherCacheDto.fromModel(copy(isRefreshing = false))),
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )
}

internal fun TravelAlertsSnapshot.toCacheEntity(json: Json): DashboardSectionCacheEntity? {
    val fetchedAt = fetchedAt ?: return null
    return DashboardSectionCacheEntity(
        sectionId = DashboardSectionId.TRAVEL_ALERTS,
        payloadJson = json.encodeToString(TravelAlertsCacheDto.fromModel(copy(isRefreshing = false))),
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )
}

internal fun LocalInfoSnapshot.toCacheEntity(json: Json): DashboardSectionCacheEntity? {
    val fetchedAt = fetchedAt ?: return null
    return DashboardSectionCacheEntity(
        sectionId = DashboardSectionId.LOCAL_INFO,
        payloadJson = json.encodeToString(LocalInfoCacheDto.fromModel(copy(isRefreshing = false))),
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )
}

internal fun FuelPriceSnapshot.toCacheEntity(json: Json): DashboardSectionCacheEntity? {
    val fetchedAt = fetchedAt ?: return null
    return DashboardSectionCacheEntity(
        sectionId = DashboardSectionId.FUEL_PRICES,
        payloadJson = json.encodeToString(FuelPriceCacheDto.fromModel(copy(isRefreshing = false))),
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )
}

internal fun EmergencyCareSnapshot.toCacheEntity(json: Json): DashboardSectionCacheEntity? {
    val fetchedAt = fetchedAt ?: return null
    return DashboardSectionCacheEntity(
        sectionId = DashboardSectionId.EMERGENCY_CARE,
        payloadJson = json.encodeToString(EmergencyCareCacheDto.fromModel(copy(isRefreshing = false))),
        fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
    )
}

internal object DashboardSectionId {
    const val TRAVEL_CONTEXT = "travel_context"
    const val WEATHER = "weather"
    const val TRAVEL_ALERTS = "travel_alerts"
    const val LOCAL_INFO = "local_info"
    const val FUEL_PRICES = "fuel_prices"
    const val EMERGENCY_CARE = "emergency_care"
}

@Serializable
private data class TravelContextCacheDto(
    val publicIp: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timeZoneId: String? = null,
    val deviceCity: String? = null,
    val deviceRegion: String? = null,
    val deviceCountry: String? = null,
    val deviceCountryCode: String? = null,
    val deviceLatitude: Double? = null,
    val deviceLongitude: Double? = null,
) {
    fun toModel(): TravelContextSnapshot = TravelContextSnapshot(
        publicIp = publicIp,
        city = city,
        region = region,
        country = country,
        countryCode = countryCode,
        latitude = latitude,
        longitude = longitude,
        timeZoneId = timeZoneId,
        deviceCity = deviceCity,
        deviceRegion = deviceRegion,
        deviceCountry = deviceCountry,
        deviceCountryCode = deviceCountryCode,
        deviceLatitude = deviceLatitude,
        deviceLongitude = deviceLongitude,
    )

    companion object {
        fun fromModel(model: TravelContextSnapshot): TravelContextCacheDto = TravelContextCacheDto(
            publicIp = model.publicIp,
            city = model.city,
            region = model.region,
            country = model.country,
            countryCode = model.countryCode,
            latitude = model.latitude,
            longitude = model.longitude,
            timeZoneId = model.timeZoneId,
            deviceCity = model.deviceCity,
            deviceRegion = model.deviceRegion,
            deviceCountry = model.deviceCountry,
            deviceCountryCode = model.deviceCountryCode,
            deviceLatitude = model.deviceLatitude,
            deviceLongitude = model.deviceLongitude,
        )
    }
}

@Serializable
private data class WeatherCacheDto(
    val currentTemperatureCelsius: Double? = null,
    val apparentTemperatureCelsius: Double? = null,
    val windSpeedKph: Double? = null,
    val windDirectionDegrees: Double? = null,
    val rainChancePercent: Int? = null,
    val summary: String,
    val conditionDescription: String,
    val weatherCode: Int? = null,
    val hourlyForecast: List<WeatherHourlyForecastSlotDto> = emptyList(),
    val dailyForecast: List<WeatherDayForecastDto> = emptyList(),
    val sourceName: String,
    val fetchedAtEpochMillis: Long? = null,
) {
    fun toModel(): WeatherSnapshot = WeatherSnapshot(
        currentTemperatureCelsius = currentTemperatureCelsius,
        apparentTemperatureCelsius = apparentTemperatureCelsius,
        windSpeedKph = windSpeedKph,
        windDirectionDegrees = windDirectionDegrees,
        rainChancePercent = rainChancePercent,
        summary = summary,
        conditionDescription = conditionDescription,
        weatherCode = weatherCode,
        hourlyForecast = hourlyForecast.map(WeatherHourlyForecastSlotDto::toModel),
        dailyForecast = dailyForecast.map(WeatherDayForecastDto::toModel),
        sourceName = sourceName,
        fetchedAt = fetchedAtEpochMillis?.let(Instant::ofEpochMilli),
    )

    companion object {
        fun fromModel(model: WeatherSnapshot): WeatherCacheDto = WeatherCacheDto(
            currentTemperatureCelsius = model.currentTemperatureCelsius,
            apparentTemperatureCelsius = model.apparentTemperatureCelsius,
            windSpeedKph = model.windSpeedKph,
            windDirectionDegrees = model.windDirectionDegrees,
            rainChancePercent = model.rainChancePercent,
            summary = model.summary,
            conditionDescription = model.conditionDescription,
            weatherCode = model.weatherCode,
            hourlyForecast = model.hourlyForecast.map(WeatherHourlyForecastSlotDto::fromModel),
            dailyForecast = model.dailyForecast.map(WeatherDayForecastDto::fromModel),
            sourceName = model.sourceName,
            fetchedAtEpochMillis = model.fetchedAt?.toEpochMilli(),
        )
    }
}

@Serializable
private data class WeatherHourlyForecastSlotDto(
    val atEpochMillis: Long,
    val summary: String,
    val temperatureCelsius: Double? = null,
    val rainChancePercent: Int? = null,
    val windSpeedKph: Double? = null,
    val windDirectionDegrees: Double? = null,
    val weatherCode: Int? = null,
) {
    fun toModel(): WeatherHourlyForecastSlot = WeatherHourlyForecastSlot(
        at = Instant.ofEpochMilli(atEpochMillis),
        summary = summary,
        temperatureCelsius = temperatureCelsius,
        rainChancePercent = rainChancePercent,
        windSpeedKph = windSpeedKph,
        windDirectionDegrees = windDirectionDegrees,
        weatherCode = weatherCode,
    )

    companion object {
        fun fromModel(model: WeatherHourlyForecastSlot): WeatherHourlyForecastSlotDto = WeatherHourlyForecastSlotDto(
            atEpochMillis = model.at.toEpochMilli(),
            summary = model.summary,
            temperatureCelsius = model.temperatureCelsius,
            rainChancePercent = model.rainChancePercent,
            windSpeedKph = model.windSpeedKph,
            windDirectionDegrees = model.windDirectionDegrees,
            weatherCode = model.weatherCode,
        )
    }
}

@Serializable
private data class WeatherDayForecastDto(
    val dateIso: String,
    val summary: String,
    val minCelsius: Double? = null,
    val maxCelsius: Double? = null,
    val rainChancePercent: Int? = null,
    val weatherCode: Int? = null,
    val windSpeedKph: Double? = null,
    val windDirectionDegrees: Double? = null,
) {
    fun toModel(): WeatherDayForecast = WeatherDayForecast(
        date = LocalDate.parse(dateIso),
        summary = summary,
        minCelsius = minCelsius,
        maxCelsius = maxCelsius,
        rainChancePercent = rainChancePercent,
        weatherCode = weatherCode,
        windSpeedKph = windSpeedKph,
        windDirectionDegrees = windDirectionDegrees,
    )

    companion object {
        fun fromModel(model: WeatherDayForecast): WeatherDayForecastDto = WeatherDayForecastDto(
            dateIso = model.date.toString(),
            summary = model.summary,
            minCelsius = model.minCelsius,
            maxCelsius = model.maxCelsius,
            rainChancePercent = model.rainChancePercent,
            weatherCode = model.weatherCode,
            windSpeedKph = model.windSpeedKph,
            windDirectionDegrees = model.windDirectionDegrees,
        )
    }
}

@Serializable
private data class TravelAlertsCacheDto(
    val enabledKinds: List<String> = emptyList(),
    val primaryCountryCode: String? = null,
    val primaryCountryName: String? = null,
    val coverageCountryCodes: List<String> = emptyList(),
    val states: List<TravelAlertSignalStateDto> = emptyList(),
    val fetchedAtEpochMillis: Long? = null,
) {
    fun toModel(): TravelAlertsSnapshot = TravelAlertsSnapshot(
        enabledKinds = enabledKinds.mapNotNull(::travelAlertKindOrNull).ifEmpty { TravelAlertKind.entries },
        primaryCountryCode = primaryCountryCode,
        primaryCountryName = primaryCountryName,
        coverageCountryCodes = coverageCountryCodes,
        states = states.mapNotNull(TravelAlertSignalStateDto::toModel),
        fetchedAt = fetchedAtEpochMillis?.let(Instant::ofEpochMilli),
    )

    companion object {
        fun fromModel(model: TravelAlertsSnapshot): TravelAlertsCacheDto = TravelAlertsCacheDto(
            enabledKinds = model.enabledKinds.map(TravelAlertKind::name),
            primaryCountryCode = model.primaryCountryCode,
            primaryCountryName = model.primaryCountryName,
            coverageCountryCodes = model.coverageCountryCodes,
            states = model.states.map(TravelAlertSignalStateDto::fromModel),
            fetchedAtEpochMillis = model.fetchedAt?.toEpochMilli(),
        )
    }
}

@Serializable
private data class TravelAlertSignalStateDto(
    val kind: String,
    val status: String,
    val signal: TravelAlertSignalSnapshotDto? = null,
    val reason: String? = null,
    val diagnosticSummary: String? = null,
    val sourceName: String,
    val sourceUrl: String? = null,
    val lastAttemptedAtEpochMillis: Long? = null,
    val lastSuccessAtEpochMillis: Long? = null,
) {
    fun toModel(): TravelAlertSignalState? {
        val resolvedKind = travelAlertKindOrNull(kind) ?: return null
        val resolvedStatus = enumValueOfOrNull<TravelAlertSignalStatus>(status) ?: return null
        return TravelAlertSignalState(
            kind = resolvedKind,
            status = resolvedStatus,
            signal = signal?.toModel(),
            reason = reason?.let(::travelAlertUnavailableReasonOrNull),
            diagnosticSummary = diagnosticSummary,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            lastAttemptedAt = lastAttemptedAtEpochMillis?.let(Instant::ofEpochMilli),
            lastSuccessAt = lastSuccessAtEpochMillis?.let(Instant::ofEpochMilli),
        )
    }

    companion object {
        fun fromModel(model: TravelAlertSignalState): TravelAlertSignalStateDto = TravelAlertSignalStateDto(
            kind = model.kind.name,
            status = model.status.name,
            signal = model.signal?.let(TravelAlertSignalSnapshotDto::fromModel),
            reason = model.reason?.name,
            diagnosticSummary = model.diagnosticSummary,
            sourceName = model.sourceName,
            sourceUrl = model.sourceUrl,
            lastAttemptedAtEpochMillis = model.lastAttemptedAt?.toEpochMilli(),
            lastSuccessAtEpochMillis = model.lastSuccessAt?.toEpochMilli(),
        )
    }
}

@Serializable
private data class TravelAlertSignalSnapshotDto(
    val kind: String,
    val severity: String,
    val title: String,
    val summary: String,
    val detailSummary: String? = null,
    val sourceName: String,
    val sourceUrl: String? = null,
    val updatedAtEpochMillis: Long,
    val affectedCountryCodes: List<String> = emptyList(),
    val itemCount: Int? = null,
) {
    fun toModel(): TravelAlertSignalSnapshot? {
        val resolvedKind = travelAlertKindOrNull(kind) ?: return null
        val resolvedSeverity = enumValueOfOrNull<TravelAlertSeverity>(severity) ?: return null
        return TravelAlertSignalSnapshot(
            kind = resolvedKind,
            severity = resolvedSeverity,
            title = title,
            summary = summary,
            detailSummary = detailSummary,
            sourceName = sourceName,
            sourceUrl = sourceUrl,
            updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
            affectedCountryCodes = affectedCountryCodes,
            itemCount = itemCount,
        )
    }

    companion object {
        fun fromModel(model: TravelAlertSignalSnapshot): TravelAlertSignalSnapshotDto = TravelAlertSignalSnapshotDto(
            kind = model.kind.name,
            severity = model.severity.name,
            title = model.title,
            summary = model.summary,
            detailSummary = model.detailSummary,
            sourceName = model.sourceName,
            sourceUrl = model.sourceUrl,
            updatedAtEpochMillis = model.updatedAt.toEpochMilli(),
            affectedCountryCodes = model.affectedCountryCodes,
            itemCount = model.itemCount,
        )
    }
}

@Serializable
private data class LocalInfoCacheDto(
    val status: String,
    val locality: String? = null,
    val region: String? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val timezone: String? = null,
    val matchedSubdivisionCode: String? = null,
    val matchedSubdivisionName: String? = null,
    val publicHoliday: LocalHolidayStatusDto? = null,
    val schoolHoliday: LocalHolidayStatusDto? = null,
    val localPriceLevel: LocalPriceLevelSnapshotDto,
    val sources: List<HolidaySourceAttributionDto> = emptyList(),
    val fetchedAtEpochMillis: Long? = null,
    val detail: String? = null,
    val note: String? = null,
) {
    fun toModel(): LocalInfoSnapshot = LocalInfoSnapshot(
        status = enumValueOfOrNull<LocalInfoStatus>(status) ?: LocalInfoStatus.UNAVAILABLE,
        locality = locality,
        region = region,
        countryCode = countryCode,
        countryName = countryName,
        timezone = timezone,
        matchedSubdivisionCode = matchedSubdivisionCode,
        matchedSubdivisionName = matchedSubdivisionName,
        publicHoliday = publicHoliday?.toModel(),
        schoolHoliday = schoolHoliday?.toModel(),
        localPriceLevel = localPriceLevel.toModel(),
        sources = sources.map(HolidaySourceAttributionDto::toModel),
        fetchedAt = fetchedAtEpochMillis?.let(Instant::ofEpochMilli),
        detail = detail,
        note = note,
    )

    companion object {
        fun fromModel(model: LocalInfoSnapshot): LocalInfoCacheDto = LocalInfoCacheDto(
            status = model.status.name,
            locality = model.locality,
            region = model.region,
            countryCode = model.countryCode,
            countryName = model.countryName,
            timezone = model.timezone,
            matchedSubdivisionCode = model.matchedSubdivisionCode,
            matchedSubdivisionName = model.matchedSubdivisionName,
            publicHoliday = model.publicHoliday?.let(LocalHolidayStatusDto::fromModel),
            schoolHoliday = model.schoolHoliday?.let(LocalHolidayStatusDto::fromModel),
            localPriceLevel = LocalPriceLevelSnapshotDto.fromModel(model.localPriceLevel),
            sources = model.sources.map(HolidaySourceAttributionDto::fromModel),
            fetchedAtEpochMillis = model.fetchedAt?.toEpochMilli(),
            detail = model.detail,
            note = model.note,
        )
    }
}

@Serializable
private data class LocalHolidayStatusDto(
    val phase: String,
    val period: HolidayPeriodDto,
) {
    fun toModel(): LocalHolidayStatus = LocalHolidayStatus(
        phase = enumValueOfOrNull<LocalHolidayPhase>(phase) ?: LocalHolidayPhase.NEXT,
        period = period.toModel(),
    )

    companion object {
        fun fromModel(model: LocalHolidayStatus): LocalHolidayStatusDto = LocalHolidayStatusDto(
            phase = model.phase.name,
            period = HolidayPeriodDto.fromModel(model.period),
        )
    }
}

@Serializable
private data class HolidayPeriodDto(
    val name: String,
    val startDateIso: String,
    val endDateIso: String,
) {
    fun toModel(): HolidayPeriod = HolidayPeriod(
        name = name,
        startDate = LocalDate.parse(startDateIso),
        endDate = LocalDate.parse(endDateIso),
    )

    companion object {
        fun fromModel(model: HolidayPeriod): HolidayPeriodDto = HolidayPeriodDto(
            name = model.name,
            startDateIso = model.startDate.toString(),
            endDateIso = model.endDate.toString(),
        )
    }
}

@Serializable
private data class HolidaySourceAttributionDto(
    val name: String,
    val url: String? = null,
) {
    fun toModel(): HolidaySourceAttribution = HolidaySourceAttribution(name = name, url = url)

    companion object {
        fun fromModel(model: HolidaySourceAttribution): HolidaySourceAttributionDto = HolidaySourceAttributionDto(
            name = model.name,
            url = model.url,
        )
    }
}

@Serializable
private data class LocalPriceLevelSnapshotDto(
    val status: String,
    val summaryBand: String? = null,
    val countryCode: String? = null,
    val countryName: String? = null,
    val rows: List<LocalPriceIndicatorRowDto> = emptyList(),
    val sources: List<String> = emptyList(),
    val fetchedAtEpochMillis: Long? = null,
    val detail: String? = null,
    val note: String? = null,
) {
    fun toModel(): LocalPriceLevelSnapshot = LocalPriceLevelSnapshot(
        status = enumValueOfOrNull<LocalPriceLevelStatus>(status) ?: LocalPriceLevelStatus.UNAVAILABLE,
        summaryBand = summaryBand?.let(::localPriceSummaryBandOrNull),
        countryCode = countryCode,
        countryName = countryName,
        rows = rows.mapNotNull(LocalPriceIndicatorRowDto::toModel),
        sources = sources,
        fetchedAt = fetchedAtEpochMillis?.let(Instant::ofEpochMilli),
        detail = detail,
        note = note,
    )

    companion object {
        fun fromModel(model: LocalPriceLevelSnapshot): LocalPriceLevelSnapshotDto = LocalPriceLevelSnapshotDto(
            status = model.status.name,
            summaryBand = model.summaryBand?.name,
            countryCode = model.countryCode,
            countryName = model.countryName,
            rows = model.rows.map(LocalPriceIndicatorRowDto::fromModel),
            sources = model.sources,
            fetchedAtEpochMillis = model.fetchedAt?.toEpochMilli(),
            detail = model.detail,
            note = model.note,
        )
    }
}

@Serializable
private data class LocalPriceIndicatorRowDto(
    val kind: String,
    val value: String,
    val detail: String,
    val precision: String,
    val source: String,
) {
    fun toModel(): LocalPriceIndicatorRow? {
        val resolvedKind = enumValueOfOrNull<LocalPriceIndicatorKind>(kind) ?: return null
        val resolvedPrecision = enumValueOfOrNull<LocalPricePrecision>(precision) ?: return null
        return LocalPriceIndicatorRow(
            kind = resolvedKind,
            value = value,
            detail = detail,
            precision = resolvedPrecision,
            source = source,
        )
    }

    companion object {
        fun fromModel(model: LocalPriceIndicatorRow): LocalPriceIndicatorRowDto = LocalPriceIndicatorRowDto(
            kind = model.kind.name,
            value = model.value,
            detail = model.detail,
            precision = model.precision.name,
            source = model.source,
        )
    }
}

@Serializable
private data class FuelPriceCacheDto(
    val status: String,
    val sourceName: String,
    val countryCode: String? = null,
    val countryName: String? = null,
    val searchRadiusKilometers: Double,
    val diesel: FuelStationPriceDto? = null,
    val gasoline: FuelStationPriceDto? = null,
    val fetchedAtEpochMillis: Long? = null,
    val detail: String,
    val note: String? = null,
) {
    fun toModel(): FuelPriceSnapshot = FuelPriceSnapshot(
        status = enumValueOfOrNull<FuelPriceStatus>(status) ?: FuelPriceStatus.UNAVAILABLE,
        sourceName = sourceName,
        countryCode = countryCode,
        countryName = countryName,
        searchRadiusKilometers = searchRadiusKilometers,
        diesel = diesel?.toModel(),
        gasoline = gasoline?.toModel(),
        fetchedAt = fetchedAtEpochMillis?.let(Instant::ofEpochMilli),
        detail = detail,
        note = note,
    )

    companion object {
        fun fromModel(model: FuelPriceSnapshot): FuelPriceCacheDto = FuelPriceCacheDto(
            status = model.status.name,
            sourceName = model.sourceName,
            countryCode = model.countryCode,
            countryName = model.countryName,
            searchRadiusKilometers = model.searchRadiusKilometers,
            diesel = model.diesel?.let(FuelStationPriceDto::fromModel),
            gasoline = model.gasoline?.let(FuelStationPriceDto::fromModel),
            fetchedAtEpochMillis = model.fetchedAt?.toEpochMilli(),
            detail = model.detail,
            note = model.note,
        )
    }
}

@Serializable
private data class FuelStationPriceDto(
    val fuelType: String,
    val stationName: String,
    val address: String? = null,
    val locality: String? = null,
    val pricePerLiter: Double,
    val currencyCode: String,
    val distanceKilometers: Double,
    val latitude: Double,
    val longitude: Double,
    val updatedAtEpochMillis: Long? = null,
    val isSelfService: Boolean? = null,
) {
    fun toModel(): FuelStationPrice? {
        val resolvedFuelType = enumValueOfOrNull<FuelType>(fuelType) ?: return null
        return FuelStationPrice(
            fuelType = resolvedFuelType,
            stationName = stationName,
            address = address,
            locality = locality,
            pricePerLiter = pricePerLiter,
            currencyCode = currencyCode,
            distanceKilometers = distanceKilometers,
            latitude = latitude,
            longitude = longitude,
            updatedAt = updatedAtEpochMillis?.let(Instant::ofEpochMilli),
            isSelfService = isSelfService,
        )
    }

    companion object {
        fun fromModel(model: FuelStationPrice): FuelStationPriceDto = FuelStationPriceDto(
            fuelType = model.fuelType.name,
            stationName = model.stationName,
            address = model.address,
            locality = model.locality,
            pricePerLiter = model.pricePerLiter,
            currencyCode = model.currencyCode,
            distanceKilometers = model.distanceKilometers,
            latitude = model.latitude,
            longitude = model.longitude,
            updatedAtEpochMillis = model.updatedAt?.toEpochMilli(),
            isSelfService = model.isSelfService,
        )
    }
}

@Serializable
private data class EmergencyCareCacheDto(
    val status: String,
    val sourceName: String,
    val countryCode: String? = null,
    val countryName: String? = null,
    val searchRadiusKilometers: Double,
    val locationSource: String? = null,
    val facility: EmergencyCareFacilityDto? = null,
    val fetchedAtEpochMillis: Long? = null,
    val detail: String,
    val note: String? = null,
) {
    fun toModel(): EmergencyCareSnapshot = EmergencyCareSnapshot(
        status = enumValueOfOrNull<EmergencyCareStatus>(status) ?: EmergencyCareStatus.UNAVAILABLE,
        sourceName = sourceName,
        countryCode = countryCode,
        countryName = countryName,
        searchRadiusKilometers = searchRadiusKilometers,
        locationSource = locationSource?.let(::emergencyCareLocationSourceOrNull),
        facility = facility?.toModel(),
        fetchedAt = fetchedAtEpochMillis?.let(Instant::ofEpochMilli),
        detail = detail,
        note = note,
    )

    companion object {
        fun fromModel(model: EmergencyCareSnapshot): EmergencyCareCacheDto = EmergencyCareCacheDto(
            status = model.status.name,
            sourceName = model.sourceName,
            countryCode = model.countryCode,
            countryName = model.countryName,
            searchRadiusKilometers = model.searchRadiusKilometers,
            locationSource = model.locationSource?.name,
            facility = model.facility?.let(EmergencyCareFacilityDto::fromModel),
            fetchedAtEpochMillis = model.fetchedAt?.toEpochMilli(),
            detail = model.detail,
            note = model.note,
        )
    }
}

@Serializable
private data class EmergencyCareFacilityDto(
    val placeId: String? = null,
    val name: String,
    val address: String? = null,
    val distanceKilometers: Double,
    val latitude: Double,
    val longitude: Double,
    val primaryType: String? = null,
) {
    fun toModel(): EmergencyCareFacility = EmergencyCareFacility(
        placeId = placeId,
        name = name,
        address = address,
        distanceKilometers = distanceKilometers,
        latitude = latitude,
        longitude = longitude,
        primaryType = primaryType,
    )

    companion object {
        fun fromModel(model: EmergencyCareFacility): EmergencyCareFacilityDto = EmergencyCareFacilityDto(
            placeId = model.placeId,
            name = model.name,
            address = model.address,
            distanceKilometers = model.distanceKilometers,
            latitude = model.latitude,
            longitude = model.longitude,
            primaryType = model.primaryType,
        )
    }
}

private inline fun <reified T> Json.decodeOrNull(value: String): T? =
    runCatching { decodeFromString<T>(value) }.getOrNull()

private inline fun <reified T : Enum<T>> enumValueOfOrNull(value: String): T? =
    runCatching { enumValueOf<T>(value) }.getOrNull()

private fun travelAlertKindOrNull(value: String): TravelAlertKind? =
    enumValueOfOrNull<TravelAlertKind>(value)

private fun travelAlertUnavailableReasonOrNull(value: String): TravelAlertUnavailableReason? =
    enumValueOfOrNull<TravelAlertUnavailableReason>(value)

private fun localPriceSummaryBandOrNull(value: String): LocalPriceSummaryBand? =
    enumValueOfOrNull<LocalPriceSummaryBand>(value)

private fun emergencyCareLocationSourceOrNull(value: String): EmergencyCareLocationSource? =
    enumValueOfOrNull<EmergencyCareLocationSource>(value)
