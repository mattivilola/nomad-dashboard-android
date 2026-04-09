package com.iloapps.nomaddashboard.core.data.di

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.data.credentials.EncryptedProviderCredentialStore
import com.iloapps.nomaddashboard.core.data.credentials.ProviderCredentialStore
import com.iloapps.nomaddashboard.core.data.emergency.EmergencyCareProvider
import com.iloapps.nomaddashboard.core.data.emergency.GooglePlacesEmergencyCareProvider
import com.iloapps.nomaddashboard.core.data.emergency.GooglePlacesNearbySearchClient
import com.iloapps.nomaddashboard.core.data.emergency.PlacesNearbySearchClient
import com.iloapps.nomaddashboard.core.data.location.AndroidVisitedDeviceLocationProvider
import com.iloapps.nomaddashboard.core.data.location.VisitedDeviceLocationProvider
import com.iloapps.nomaddashboard.core.data.localprice.DefaultLocalPriceLevelProvider
import com.iloapps.nomaddashboard.core.data.localprice.LocalPriceLevelProvider
import com.iloapps.nomaddashboard.core.data.localinfo.DefaultLocalInfoProvider
import com.iloapps.nomaddashboard.core.data.localinfo.LocalInfoProvider
import com.iloapps.nomaddashboard.core.data.monitor.TelemetryReader
import com.iloapps.nomaddashboard.core.data.fuel.DefaultFuelPriceProvider
import com.iloapps.nomaddashboard.core.data.fuel.FuelPriceProvider
import com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.data.timetracking.RoomTimeTrackingRepository
import com.iloapps.nomaddashboard.core.data.timetracking.RoomTimeTrackingTransactionRunner
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingRepository
import com.iloapps.nomaddashboard.core.data.timetracking.TimeTrackingTransactionRunner
import com.iloapps.nomaddashboard.core.data.travelalerts.AndroidWebViewSmartravellerBrowserFetcher
import com.iloapps.nomaddashboard.core.data.travelalerts.SmartravellerBrowserFetcher
import com.iloapps.nomaddashboard.core.data.visited.DatabaseTransactionRunner
import com.iloapps.nomaddashboard.core.data.visited.RoomDatabaseTransactionRunner
import com.iloapps.nomaddashboard.core.data.visited.RoomVisitedHistoryStore
import com.iloapps.nomaddashboard.core.data.visited.VisitedHistoryStore
import com.iloapps.nomaddashboard.core.database.NomadDatabase
import com.iloapps.nomaddashboard.core.database.dao.MetricPointDao
import com.iloapps.nomaddashboard.core.database.dao.LocalPriceCacheDao
import com.iloapps.nomaddashboard.core.database.dao.LocalInfoCacheDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingEntryDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingInterruptionDao
import com.iloapps.nomaddashboard.core.database.dao.TimeTrackingProjectDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedCountryDayDao
import com.iloapps.nomaddashboard.core.database.dao.VisitedPlaceDao
import com.iloapps.nomaddashboard.core.datastore.AppSettingsSerializer
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.network.api.FranceFuelPriceService
import com.iloapps.nomaddashboard.core.network.api.CensusGeocoderService
import com.iloapps.nomaddashboard.core.network.api.EurostatService
import com.iloapps.nomaddashboard.core.network.api.FreeIpApiService
import com.iloapps.nomaddashboard.core.network.api.HudUserFmrService
import com.iloapps.nomaddashboard.core.network.api.IpifyService
import com.iloapps.nomaddashboard.core.network.api.NagerDateService
import com.iloapps.nomaddashboard.core.network.api.ItalyFuelPriceService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoMarineService
import com.iloapps.nomaddashboard.core.network.api.OpenHolidaysService
import com.iloapps.nomaddashboard.core.network.api.ReliefWebReportsService
import com.iloapps.nomaddashboard.core.network.api.SmartravellerService
import com.iloapps.nomaddashboard.core.network.api.SpainFuelPriceService
import com.iloapps.nomaddashboard.core.network.api.TankerkoenigService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import retrofit2.Retrofit
import javax.inject.Singleton
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
object NomadInfrastructureModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val original = chain.request()
            val request: Request = original.newBuilder()
                .apply {
                    if (original.header("Accept").isNullOrBlank()) {
                        header("Accept", "application/json")
                    }
                    if (original.header("User-Agent").isNullOrBlank()) {
                        header("User-Agent", "NomadDashboard-Android/1")
                    }
                }
                .build()
            chain.proceed(request)
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideFreeIpApiService(
        client: OkHttpClient,
        json: Json,
    ): FreeIpApiService =
        Retrofit.Builder()
            .baseUrl("https://free.freeipapi.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FreeIpApiService::class.java)

    @Provides
    @Singleton
    fun provideEurostatService(
        client: OkHttpClient,
        json: Json,
    ): EurostatService =
        Retrofit.Builder()
            .baseUrl("https://ec.europa.eu/eurostat/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EurostatService::class.java)

    @Provides
    @Singleton
    fun provideCensusGeocoderService(
        client: OkHttpClient,
        json: Json,
    ): CensusGeocoderService =
        Retrofit.Builder()
            .baseUrl("https://geocoding.geo.census.gov/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CensusGeocoderService::class.java)

    @Provides
    @Singleton
    fun provideHudUserFmrService(
        client: OkHttpClient,
        json: Json,
    ): HudUserFmrService =
        Retrofit.Builder()
            .baseUrl("https://www.huduser.gov/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HudUserFmrService::class.java)

    @Provides
    @Singleton
    fun provideNagerDateService(
        client: OkHttpClient,
        json: Json,
    ): NagerDateService =
        Retrofit.Builder()
            .baseUrl("https://date.nager.at/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NagerDateService::class.java)

    @Provides
    @Singleton
    fun provideOpenHolidaysService(
        client: OkHttpClient,
        json: Json,
    ): OpenHolidaysService =
        Retrofit.Builder()
            .baseUrl("https://openholidaysapi.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenHolidaysService::class.java)

    @Provides
    @Singleton
    fun provideIpifyService(
        client: OkHttpClient,
        json: Json,
    ): IpifyService =
        Retrofit.Builder()
            .baseUrl("https://api.ipify.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(IpifyService::class.java)

    @Provides
    @Singleton
    fun provideOpenMeteoService(
        client: OkHttpClient,
        json: Json,
    ): OpenMeteoService =
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoService::class.java)

    @Provides
    @Singleton
    fun provideOpenMeteoMarineService(
        client: OkHttpClient,
        json: Json,
    ): OpenMeteoMarineService =
        Retrofit.Builder()
            .baseUrl("https://marine-api.open-meteo.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoMarineService::class.java)

    @Provides
    @Singleton
    fun provideSpainFuelPriceService(
        client: OkHttpClient,
        json: Json,
    ): SpainFuelPriceService =
        Retrofit.Builder()
            .baseUrl("https://sedeaplicaciones.minetur.gob.es/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SpainFuelPriceService::class.java)

    @Provides
    @Singleton
    fun provideFranceFuelPriceService(
        client: OkHttpClient,
        json: Json,
    ): FranceFuelPriceService =
        Retrofit.Builder()
            .baseUrl("https://data.economie.gouv.fr/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FranceFuelPriceService::class.java)

    @Provides
    @Singleton
    fun provideItalyFuelPriceService(
        client: OkHttpClient,
        json: Json,
    ): ItalyFuelPriceService =
        Retrofit.Builder()
            .baseUrl("https://www.mimit.gov.it/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ItalyFuelPriceService::class.java)

    @Provides
    @Singleton
    fun provideTankerkoenigService(
        client: OkHttpClient,
        json: Json,
    ): TankerkoenigService =
        Retrofit.Builder()
            .baseUrl("https://creativecommons.tankerkoenig.de/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TankerkoenigService::class.java)

    @Provides
    @Singleton
    fun provideSmartravellerService(
        client: OkHttpClient,
        json: Json,
    ): SmartravellerService =
        Retrofit.Builder()
            .baseUrl("https://www.smartraveller.gov.au/")
            .client(
                client.newBuilder()
                    .protocols(listOf(Protocol.HTTP_1_1))
                    .build(),
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SmartravellerService::class.java)

    @Provides
    @Singleton
    fun provideReliefWebReportsService(
        client: OkHttpClient,
        json: Json,
    ): ReliefWebReportsService =
        Retrofit.Builder()
            .baseUrl("https://api.reliefweb.int/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ReliefWebReportsService::class.java)

    @Provides
    @Singleton
    fun provideNomadDatabase(@ApplicationContext context: Context): NomadDatabase =
        Room.databaseBuilder(context, NomadDatabase::class.java, "nomad-dashboard.db")
            .addMigrations(NomadDatabase.MIGRATION_1_2)
            .addMigrations(NomadDatabase.MIGRATION_2_3)
            .addMigrations(NomadDatabase.MIGRATION_3_4)
            .addMigrations(NomadDatabase.MIGRATION_4_5)
            .addMigrations(NomadDatabase.MIGRATION_5_6)
            .build()

    @Provides
    fun provideMetricPointDao(database: NomadDatabase): MetricPointDao = database.metricPointDao()

    @Provides
    fun provideLocalPriceCacheDao(database: NomadDatabase): LocalPriceCacheDao = database.localPriceCacheDao()

    @Provides
    fun provideLocalInfoCacheDao(database: NomadDatabase): LocalInfoCacheDao = database.localInfoCacheDao()

    @Provides
    fun provideVisitedPlaceDao(database: NomadDatabase): VisitedPlaceDao = database.visitedPlaceDao()

    @Provides
    fun provideVisitedCountryDayDao(database: NomadDatabase): VisitedCountryDayDao = database.visitedCountryDayDao()

    @Provides
    fun provideTimeTrackingProjectDao(database: NomadDatabase): TimeTrackingProjectDao = database.timeTrackingProjectDao()

    @Provides
    fun provideTimeTrackingEntryDao(database: NomadDatabase): TimeTrackingEntryDao = database.timeTrackingEntryDao()

    @Provides
    fun provideTimeTrackingInterruptionDao(database: NomadDatabase): TimeTrackingInterruptionDao =
        database.timeTrackingInterruptionDao()

    @Provides
    @Singleton
    fun provideNomadSettingsDataSource(
        @ApplicationContext context: Context,
    ): NomadSettingsDataSource =
        NomadSettingsDataSource(
            androidx.datastore.core.DataStoreFactory.create(
                serializer = AppSettingsSerializer,
                produceFile = { context.dataStoreFile("app-settings.pb") },
            ),
        )

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NomadRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNomadDashboardRepository(
        impl: DefaultNomadDashboardRepository,
    ): NomadDashboardRepository

    @Binds
    @Singleton
    abstract fun bindTelemetryReader(
        impl: com.iloapps.nomaddashboard.core.data.monitor.SystemTelemetryReader,
    ): TelemetryReader

    @Binds
    @Singleton
    abstract fun bindVisitedHistoryStore(
        impl: RoomVisitedHistoryStore,
    ): VisitedHistoryStore

    @Binds
    @Singleton
    abstract fun bindVisitedLocationProvider(
        impl: AndroidVisitedDeviceLocationProvider,
    ): VisitedDeviceLocationProvider

    @Binds
    @Singleton
    abstract fun bindFuelPriceProvider(
        impl: DefaultFuelPriceProvider,
    ): FuelPriceProvider

    @Binds
    @Singleton
    abstract fun bindLocalPriceLevelProvider(
        impl: DefaultLocalPriceLevelProvider,
    ): LocalPriceLevelProvider

    @Binds
    @Singleton
    abstract fun bindLocalInfoProvider(
        impl: DefaultLocalInfoProvider,
    ): LocalInfoProvider

    @Binds
    @Singleton
    abstract fun bindEmergencyCareProvider(
        impl: GooglePlacesEmergencyCareProvider,
    ): EmergencyCareProvider

    @Binds
    @Singleton
    abstract fun bindPlacesNearbySearchClient(
        impl: GooglePlacesNearbySearchClient,
    ): PlacesNearbySearchClient

    @Binds
    @Singleton
    abstract fun bindProviderCredentialStore(
        impl: EncryptedProviderCredentialStore,
    ): ProviderCredentialStore

    @Binds
    @Singleton
    abstract fun bindSmartravellerBrowserFetcher(
        impl: AndroidWebViewSmartravellerBrowserFetcher,
    ): SmartravellerBrowserFetcher

    @Binds
    @Singleton
    abstract fun bindTimeTrackingRepository(
        impl: RoomTimeTrackingRepository,
    ): TimeTrackingRepository

    @Binds
    @Singleton
    abstract fun bindTimeTrackingTransactionRunner(
        impl: RoomTimeTrackingTransactionRunner,
    ): TimeTrackingTransactionRunner

    @Binds
    @Singleton
    abstract fun bindDatabaseTransactionRunner(
        impl: RoomDatabaseTransactionRunner,
    ): DatabaseTransactionRunner
}
