package com.iloapps.nomaddashboard.di

import com.iloapps.nomaddashboard.BuildConfig
import com.iloapps.nomaddashboard.core.data.travelalerts.TravelAlertProviderAppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    @Provides
    @Singleton
    fun provideTravelAlertProviderAppConfig(): TravelAlertProviderAppConfig =
        TravelAlertProviderAppConfig(
            reliefWebAppName = BuildConfig.RELIEFWEB_APP_NAME,
        )
}
