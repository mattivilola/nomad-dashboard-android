package com.iloapps.nomaddashboard.core.travel.answers

import com.iloapps.nomaddashboard.core.model.DashboardSnapshot
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightLocation
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightOutlook
import com.iloapps.nomaddashboard.core.travel.daylight.DaylightReference
import java.time.Instant
import java.time.ZoneId

/** Today's light for the place a dashboard snapshot is about, shared by the spoken answers and the widgets. */
object SnapshotDaylight {
    /**
     * The device location first, else the IP location, the same order the heads-up engine uses. The
     * device location has no zone of its own, so it uses the device's [zone]; the IP location brings
     * its own. Null without a location with coordinates.
     */
    fun outlook(snapshot: DashboardSnapshot, now: Instant, zone: ZoneId): DaylightOutlook? {
        val context = snapshot.travelContext
        val reference = DaylightReference.resolve(
            deviceLocation = DaylightLocation(context.deviceLatitude, context.deviceLongitude, zoneId = null),
            ipLocation = DaylightLocation(context.latitude, context.longitude, context.timeZoneId),
            snapshotZoneId = zone.id,
            fallbackZone = zone,
        ) ?: return null
        return DaylightOutlook.of(reference, now)
    }
}
