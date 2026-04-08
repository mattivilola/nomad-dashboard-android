package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_price_cache")
data class LocalPriceCacheEntity(
    @PrimaryKey val cacheKey: String,
    val status: String,
    val summaryBand: String?,
    val countryCode: String?,
    val countryName: String?,
    val rowsJson: String,
    val sourcesJson: String,
    val fetchedAtEpochMillis: Long?,
    val detail: String?,
    val note: String?,
)
