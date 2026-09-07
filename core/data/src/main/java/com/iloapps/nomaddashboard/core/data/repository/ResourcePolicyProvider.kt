package com.iloapps.nomaddashboard.core.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.PowerManager
import com.iloapps.nomaddashboard.core.model.ResourceMode
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

data class ResourcePolicy(val lowImpact: Boolean = false, val reason: String = "Normal data use")

fun interface ResourcePolicyProvider {
    fun current(mode: ResourceMode): ResourcePolicy
}

internal object DefaultResourcePolicyProvider : ResourcePolicyProvider {
    override fun current(mode: ResourceMode) = ResourcePolicy(
        lowImpact = mode == ResourceMode.LOW_DATA,
        reason = if (mode == ResourceMode.LOW_DATA) "Low Data" else "Normal data use",
    )
}

@Module
@InstallIn(SingletonComponent::class)
object ResourcePolicyModule {
    @Provides
    @Singleton
    fun provideResourcePolicy(@ApplicationContext context: Context): ResourcePolicyProvider = ResourcePolicyProvider { mode ->
        when (mode) {
            ResourceMode.NORMAL -> ResourcePolicy()
            ResourceMode.LOW_DATA -> ResourcePolicy(true, "Low Data")
            ResourceMode.AUTO -> {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                val power = context.getSystemService(PowerManager::class.java)
                val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
                val reason = when {
                    power?.isPowerSaveMode == true -> "Auto · Battery Saver"
                    (power?.currentThermalStatus ?: 0) >= PowerManager.THERMAL_STATUS_MODERATE -> "Auto · Device cooling"
                    connectivity?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "Auto · Data Saver"
                    capabilities != null && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING) -> "Auto · Roaming"
                    connectivity?.isActiveNetworkMetered == true -> "Auto · Metered connection"
                    else -> null
                }
                ResourcePolicy(reason != null, reason ?: "Auto · Normal data use")
            }
        }
    }
}
