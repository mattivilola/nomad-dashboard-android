package com.iloapps.nomaddashboard.core.database

import androidx.room.TypeConverter
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource
import com.iloapps.nomaddashboard.core.model.VisitedCountryDayProvenance

class NomadDatabaseConverters {
    @TypeConverter
    fun fromVisitedPlaceSource(value: VisitedPlaceSource): String = value.name

    @TypeConverter
    fun toVisitedPlaceSource(value: String): VisitedPlaceSource =
        VisitedPlaceSource.entries.firstOrNull { it.name == value } ?: VisitedPlaceSource.PUBLIC_IP_GEOLOCATION

    @TypeConverter
    fun fromVisitedPlaceSources(values: List<VisitedPlaceSource>): String =
        values.joinToString(",") { it.name }

    @TypeConverter
    fun toVisitedPlaceSources(value: String): List<VisitedPlaceSource> =
        value.split(',')
            .mapNotNull { raw ->
                val normalized = raw.trim()
                if (normalized.isEmpty()) {
                    null
                } else {
                    VisitedPlaceSource.entries.firstOrNull { it.name == normalized }
                }
            }

    @TypeConverter
    fun fromVisitedCountryDayProvenance(value: VisitedCountryDayProvenance): String = value.name

    @TypeConverter
    fun toVisitedCountryDayProvenance(value: String): VisitedCountryDayProvenance =
        VisitedCountryDayProvenance.entries.firstOrNull { it.name == value }
            ?: VisitedCountryDayProvenance.OBSERVED
}
