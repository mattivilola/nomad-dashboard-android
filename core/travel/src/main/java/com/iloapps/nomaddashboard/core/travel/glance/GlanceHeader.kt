package com.iloapps.nomaddashboard.core.travel.glance

import com.iloapps.nomaddashboard.core.model.TravelContextSnapshot

/** The city, region and country of one location source (the device or the public IP). */
data class GlancePlace(
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
) {
    /** "City, Country" with blank parts dropped, or null when neither is known. */
    val cityCountryDescription: String?
        get() = listOfNotNull(city.trimmedNonEmpty(), country.trimmedNonEmpty())
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")

    companion object {
        fun device(context: TravelContextSnapshot): GlancePlace =
            GlancePlace(context.deviceCity, context.deviceRegion, context.deviceCountry)

        fun publicIp(context: TravelContextSnapshot): GlancePlace =
            GlancePlace(context.city, context.region, context.country)
    }
}

/**
 * The location-led header at the top of the dashboard: the place the user is in and a caption that
 * compares the device location with the public IP's.
 */
data class GlanceHeader(
    /** The device city, then the IP city, then a generic line. */
    val headline: String,
    /**
     * Caption candidates from most to least detailed. The UI shows the first one that fits (see
     * [variantThatFits]), so the IP comparison is never the part that gets cut off:
     * "region, country · IP city, country", then "country · IP city, country", then "country · IP city".
     */
    val detailVariants: List<DetailVariant>,
) {
    /** One candidate for the caption. The device part is shown with a location icon, the IP part with an "IP" label. */
    data class DetailVariant(
        val device: String?,
        val ip: String?,
    )

    /**
     * The first caption variant [fits] accepts; the shortest one when none does. Null without any
     * location detail.
     */
    fun variantThatFits(fits: (DetailVariant) -> Boolean): DetailVariant? =
        detailVariants.firstOrNull(fits) ?: detailVariants.lastOrNull()

    /**
     * How to lay the caption out: on one line when a variant [fitsOnOneLine], else the shortest variant
     * with the device and IP parts on two lines, so the IP comparison is still never cut off. Null
     * without any location detail.
     */
    fun captionLayout(fitsOnOneLine: (DetailVariant) -> Boolean): CaptionLayout? {
        detailVariants.firstOrNull(fitsOnOneLine)?.let { return CaptionLayout.SingleLine(it) }
        return detailVariants.lastOrNull()?.let { CaptionLayout.Stacked(it) }
    }

    sealed interface CaptionLayout {
        val variant: DetailVariant

        data class SingleLine(override val variant: DetailVariant) : CaptionLayout

        /** The device part on one line and the IP part on the next. */
        data class Stacked(override val variant: DetailVariant) : CaptionLayout
    }

    companion object {
        const val FALLBACK_HEADLINE = "Travel-ready system telemetry"

        fun of(device: GlancePlace?, ip: GlancePlace?): GlanceHeader {
            val deviceCity = device?.city.trimmedNonEmpty()
            val headline = deviceCity ?: ip?.city.trimmedNonEmpty() ?: FALLBACK_HEADLINE

            // A region or country that repeats the headline (or each other) is not shown again.
            val seen = mutableSetOf<String>()
            deviceCity?.let { seen += it.lowercase() }
            val region = device?.region.trimmedNonEmpty()?.takeIf { seen.add(it.lowercase()) }
            val country = device?.country.trimmedNonEmpty()?.takeIf { seen.add(it.lowercase()) }
            val regionAndCountry = listOfNotNull(region, country)

            val deviceDetails = listOf(
                regionAndCountry.takeIf { it.isNotEmpty() }?.joinToString(", "),
                country,
                country,
            )
            val ipCityCountry = ip?.cityCountryDescription
            val ipDetails = listOf(
                ipCityCountry,
                ipCityCountry,
                ip?.city.trimmedNonEmpty() ?: ipCityCountry,
            )

            val variants = mutableListOf<DetailVariant>()
            deviceDetails.zip(ipDetails).forEach { (deviceDetail, ipDetail) ->
                if (deviceDetail == null && ipDetail == null) return@forEach
                val variant = DetailVariant(deviceDetail, ipDetail)
                if (variants.lastOrNull() != variant) variants += variant
            }

            return GlanceHeader(headline = headline, detailVariants = variants)
        }

        fun of(context: TravelContextSnapshot): GlanceHeader =
            of(device = GlancePlace.device(context), ip = GlancePlace.publicIp(context))
    }
}
