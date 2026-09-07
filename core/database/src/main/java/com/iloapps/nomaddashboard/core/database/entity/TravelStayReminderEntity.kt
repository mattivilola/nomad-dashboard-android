package com.iloapps.nomaddashboard.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.iloapps.nomaddashboard.core.model.TravelStayReminder
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "travel_stay_reminders")
data class TravelStayReminderEntity(
    @PrimaryKey val id: String,
    val label: String,
    val country: String?,
    val targetDateIso: String,
    val createdAtEpochMillis: Long,
    val note: String?,
)

fun TravelStayReminderEntity.toExternalModel(): TravelStayReminder = TravelStayReminder(
    id = id,
    label = label,
    country = country,
    targetDate = LocalDate.parse(targetDateIso),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    note = note,
)

fun TravelStayReminder.toEntity(): TravelStayReminderEntity = TravelStayReminderEntity(
    id = id,
    label = label,
    country = country,
    targetDateIso = targetDate.toString(),
    createdAtEpochMillis = createdAt.toEpochMilli(),
    note = note,
)
