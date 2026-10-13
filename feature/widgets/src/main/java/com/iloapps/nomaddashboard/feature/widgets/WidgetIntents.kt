package com.iloapps.nomaddashboard.feature.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.iloapps.nomaddashboard.core.travel.answers.NomadDeepLink

/** The intents the widgets and the Quick Settings tile use to open the app. */
internal object WidgetIntents {
    /**
     * Opens [link] in this app. Restricting the intent to the app's own package keeps another app that
     * declares the same scheme from receiving it, and works for a debug build installed next to the
     * release app.
     */
    fun view(context: Context, link: NomadDeepLink): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(link.uri()))
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
