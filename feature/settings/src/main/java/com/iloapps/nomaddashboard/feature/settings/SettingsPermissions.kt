package com.iloapps.nomaddashboard.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal enum class LocationAccess { NONE, APPROXIMATE, PRECISE }

internal class LocationPermissionState(
    val access: LocationAccess,
    val request: () -> Unit,
    val openAppSettings: () -> Unit,
) {
    val isGranted: Boolean get() = access != LocationAccess.NONE
}

internal class NotificationPermissionState(
    val isGranted: Boolean,
    val request: () -> Unit,
    val openNotificationSettings: () -> Unit,
)

/** The current Android location permission, refreshed when the app returns from system settings. */
@Composable
internal fun rememberLocationPermission(): LocationPermissionState {
    val context = LocalContext.current
    var access by remember { mutableStateOf(context.locationAccess()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = context.locationAccess()
    }
    OnResume { access = context.locationAccess() }
    return LocationPermissionState(
        access = access,
        request = {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        },
        openAppSettings = { context.startActivity(appDetailsIntent(context)) },
    )
}

/**
 * The Android notification permission. [onResult] receives the outcome of a request so the caller can
 * switch its feature on only when the user agreed.
 */
@Composable
internal fun rememberNotificationPermission(onResult: (Boolean) -> Unit = {}): NotificationPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.canPostNotifications()) }
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = context.canPostNotifications()
        currentOnResult(result)
    }
    OnResume { granted = context.canPostNotifications() }
    return NotificationPermissionState(
        isGranted = granted,
        request = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                currentOnResult(true)
            }
        },
        openNotificationSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

@Composable
private fun OnResume(action: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentAction by rememberUpdatedState(action)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentAction()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun Context.locationAccess(): LocationAccess = when {
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationAccess.PRECISE
    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationAccess.APPROXIMATE
    else -> LocationAccess.NONE
}

private fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun appDetailsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
