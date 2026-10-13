package com.iloapps.nomaddashboard.core.designsystem.component

import android.content.Context
import android.content.pm.PackageManager

/**
 * Whether this build carries a Google Maps SDK key. The key is filled into the manifest at build time
 * from local configuration; an unresolved `${...}` placeholder means there is none, and map surfaces
 * fall back to opening the external maps app.
 */
fun Context.hasConfiguredMapsApiKey(): Boolean {
    val metaData = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData
    }.getOrNull()

    val keyValue = metaData?.getString("com.google.android.geo.API_KEY").orEmpty().trim()
    return keyValue.isNotBlank() && keyValue.startsWith("\${").not()
}
