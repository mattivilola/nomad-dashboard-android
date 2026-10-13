package com.iloapps.nomaddashboard.core.travel.safety

/** Which country the user is in, for the safety cards. */
object CurrentCountry {
    /**
     * Upper-cased ISO country code of where the user currently is: device location first, then
     * IP geolocation, then the local info card. Blank codes are skipped.
     */
    fun code(deviceCountryCode: String?, ipCountryCode: String?, localInfoCountryCode: String?): String? =
        listOf(deviceCountryCode, ipCountryCode, localInfoCountryCode)
            .asSequence()
            .mapNotNull { it?.normalizedCountryCode() }
            .firstOrNull { it.isNotEmpty() }
}
