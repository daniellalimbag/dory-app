package com.thesisapp.data

import androidx.room.*

@Dao
interface MlResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mlResult: MlResult)

    @Query("SELECT MAX(sessionId) FROM ml_results")
    suspend fun getMaxSessionId(): Int?

    @Query("SELECT * FROM ml_results ORDER BY date DESC, timeStart DESC")
    suspend fun getSessionSummaries(): List<MlResult>

    @Query("SELECT * FROM ml_results WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBySessionId(sessionId: Int): MlResult?

    @Update
    suspend fun update(mlResult: MlResult)

    @Query("DELETE FROM ml_results")
    suspend fun clearAll()
}