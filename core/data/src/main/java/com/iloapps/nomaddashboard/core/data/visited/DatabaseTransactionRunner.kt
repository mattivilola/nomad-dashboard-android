package com.iloapps.nomaddashboard.core.data.visited

import androidx.room.withTransaction
import com.iloapps.nomaddashboard.core.database.NomadDatabase
import javax.inject.Inject
import javax.inject.Singleton

interface DatabaseTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

@Singleton
class RoomDatabaseTransactionRunner @Inject constructor(
    private val database: NomadDatabase,
) : DatabaseTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction {
            block()
        }
}
