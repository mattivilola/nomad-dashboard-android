package com.iloapps.nomaddashboard.core.database

import androidx.room.TypeConverter
import com.iloapps.nomaddashboard.core.model.VisitedPlaceSource

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
}
