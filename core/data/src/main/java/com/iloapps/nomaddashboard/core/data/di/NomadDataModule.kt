package com.iloapps.nomaddashboard.core.data.di

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.iloapps.nomaddashboard.core.common.ApplicationScope
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.data.repository.DefaultNomadDashboardRepository
import com.iloapps.nomaddashboard.core.data.repository.NomadDashboardRepository
import com.iloapps.nomaddashboard.core.database.NomadDatabase
import com.iloapps.nomaddashboard.core.datastore.AppSettingsSerializer
import com.iloapps.nomaddashboard.core.datastore.NomadSettingsDataSource
import com.iloapps.nomaddashboard.core.network.api.FreeIpApiService
import com.iloapps.nomaddashboard.core.network.api.OpenMeteoService
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
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

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
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
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
    fun provideNomadDatabase(@ApplicationContext context: Context): NomadDatabase =
        Room.databaseBuilder(context, NomadDatabase::class.java, "nomad-dashboard.db").build()

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
}
