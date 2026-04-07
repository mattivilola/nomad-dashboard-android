package com.iloapps.nomaddashboard.core.data.travelalerts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface NeighborCountryResolver {
    fun neighboringCountryCodes(countryCode: String): List<String>
}

@Singleton
class BundledNeighborCountryResolver private constructor(
    private val bordersByCountry: Map<String, List<String>>,
) : NeighborCountryResolver {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        parseBorderMap {
            context.assets.open(COUNTRY_BORDERS_ASSET_PATH)
        },
    )

    override fun neighboringCountryCodes(countryCode: String): List<String> =
        bordersByCountry[countryCode.uppercase(Locale.US)].orEmpty()

    companion object {
        const val COUNTRY_BORDERS_ASSET_PATH = "country-borders.json"

        internal fun fromRecords(records: Map<String, List<String>>): BundledNeighborCountryResolver =
            BundledNeighborCountryResolver(
                records.mapKeys { it.key.uppercase(Locale.US) }
                    .mapValues { (_, value) -> value.map { it.uppercase(Locale.US) } },
            )
    }
}

private fun parseBorderMap(loader: () -> InputStream): Map<String, List<String>> =
    runCatching {
        loader().use { input ->
            Json { ignoreUnknownKeys = true }
                .decodeFromString<List<CountryBorderRecord>>(input.readBytes().decodeToString())
        }
    }.getOrDefault(emptyList())
        .associate { record ->
            record.cca2.uppercase(Locale.US) to record.borders.map { it.uppercase(Locale.US) }
        }

@Serializable
internal data class CountryBorderRecord(
    val cca2: String,
    val borders: List<String>,
)
