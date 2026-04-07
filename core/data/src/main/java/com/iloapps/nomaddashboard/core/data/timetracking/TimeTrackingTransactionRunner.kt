package com.iloapps.nomaddashboard.core.data.timetracking

import androidx.room.withTransaction
import com.iloapps.nomaddashboard.core.database.NomadDatabase
import javax.inject.Inject
import javax.inject.Singleton

interface TimeTrackingTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

@Singleton
class RoomTimeTrackingTransactionRunner @Inject constructor(
    private val database: NomadDatabase,
) : TimeTrackingTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction(block)
}
