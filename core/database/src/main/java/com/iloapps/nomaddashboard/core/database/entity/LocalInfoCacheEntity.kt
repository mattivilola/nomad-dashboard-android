package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_info_cache")
data class LocalInfoCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payloadJson: String,
    val fetchedAtEpochMillis: Long,
)
