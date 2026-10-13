package com.iloapps.nomaddashboard.feature.companion

import com.iloapps.nomaddashboard.core.travel.safety.CountryNames
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyEssentials
import com.iloapps.nomaddashboard.core.travel.safety.EmbassyOverviewBuilder
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumberDirectory
import com.iloapps.nomaddashboard.core.travel.safety.EmergencyNumbers
import com.iloapps.nomaddashboard.core.travel.safety.SafetyLocation
import com.iloapps.nomaddashboard.core.travel.safety.build
import java.util.Locale

/**
 * The safety block at the top of the Offline essentials: who to call in the current country and how to
 * reach the user's own embassy. Both come from datasets bundled with the app, so they are available
 * offline. Citizenship and location are only compared with those datasets and never leave the device.
 *
 * Unverified countries show nothing rather than a guess.
 */
data class SafetyEssentials(
    /** The country the numbers are for, in the user's language. */
    val countryName: String? = null,
    val emergencyNumbers: EmergencyNumbers? = null,
    val embassy: EmbassyEssentials? = null,
) {
    val isEmpty: Boolean
        get() = emergencyNumbers == null && embassy == null

    companion object {
        val Empty = SafetyEssentials()
    }
}

/** Decides the [SafetyEssentials] from the user's citizenships and where they are. */
class SafetyEssentialsBuilder(
    private val embassyOverviewBuilder: EmbassyOverviewBuilder = EmbassyOverviewBuilder(),
    private val countryNames: CountryNames = CountryNames.forLocale(Locale.getDefault()),
) {
    /** Reads the bundled mission dataset once the user is abroad, so call it off the main thread. */
    fun build(citizenships: List<String>, location: SafetyLocation): SafetyEssentials {
        val numbers = EmergencyNumberDirectory.numbersFor(location.countryCode)
        val embassy = embassyOverviewBuilder.build(citizenships, location).essentials()
        return SafetyEssentials(
            countryName = location.countryCode?.takeIf { numbers != null }?.let(countryNames::name),
            emergencyNumbers = numbers,
            embassy = embassy,
        )
    }
}
