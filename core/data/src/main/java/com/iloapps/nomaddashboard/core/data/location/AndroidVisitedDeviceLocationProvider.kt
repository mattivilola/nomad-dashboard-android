package com.iloapps.nomaddashboard.core.data.location

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

@Singleton
class AndroidVisitedDeviceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VisitedDeviceLocationProvider {
    override fun hasLocationPermission(): Boolean = hasLocationPermissionInternal()

    override suspend fun currentLocation(): DeviceLocationSnapshot {
        if (hasLocationPermissionInternal().not()) {
            return DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.PERMISSION_MISSING)
        }

        val location = currentCoordinates()
            ?: return DeviceLocationSnapshot(status = DeviceLocationResolutionStatus.NO_FIX)
        val details = withTimeoutOrNull(ReverseGeocodeTimeoutMillis) {
            reverseGeocode(location)
        }
        val country = details?.countryName?.trim()?.takeIf(String::isNotBlank)
        return DeviceLocationSnapshot(
            status = if (country != null) {
                DeviceLocationResolutionStatus.PLACE_RESOLVED
            } else {
                DeviceLocationResolutionStatus.COORDINATES_ONLY
            },
            city = details?.locality?.trim()?.takeIf(String::isNotBlank),
            region = details?.adminArea?.trim()?.takeIf(String::isNotBlank),
            country = country,
            countryCode = details?.countryCode?.trim()?.takeIf(String::isNotBlank)?.uppercase(),
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentCoordinates(): Location? {
        val locationManager = context.getSystemService<LocationManager>() ?: return null
        val providers = buildList {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (hasFineLocationPermission() && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
                add(LocationManager.PASSIVE_PROVIDER)
            }
        }.distinct()

        providers.firstNotNullOfOrNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }?.let { return it }

        return providers.firstNotNullOfOrNull { provider ->
            currentLocationFromProvider(locationManager, provider)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocationFromProvider(
        locationManager: LocationManager,
        provider: String,
    ): Location? = withTimeoutOrNull(10_000) {
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }

            runCatching {
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    ContextCompat.getMainExecutor(context),
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            }.onFailure {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private suspend fun reverseGeocode(location: Location): android.location.Address? {
        if (Geocoder.isPresent().not()) {
            return null
        }

        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                runCatching {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1) { results ->
                        if (continuation.isActive) {
                            continuation.resume(results.firstOrNull())
                        }
                    }
                }.onFailure {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        } else {
            withContext(ioDispatcher) {
                @Suppress("DEPRECATION")
                runCatching {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                }.getOrNull()
            }
        }
    }

    private fun hasLocationPermissionInternal(): Boolean =
        hasFineLocationPermission() || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private const val ReverseGeocodeTimeoutMillis = 1_500L
