package com.thesisapp.data

import androidx.room.*

@Dao
interface MlResultDao {
    @Insert
    suspend fun insert(mlResult: MlResult)

    @Query("SELECT MAX(sessionId) FROM ml_results")
    fun getMaxSessionId(): Int?

    @Query(" SELECT * FROM ml_results GROUP BY sessionId ORDER BY sessionId DESC")
    fun getSessionSummaries(): List<MlResult>

    @Query("SELECT * FROM ml_results WHERE sessionId = :sessionId")
    fun getBySessionId(sessionId: Int): MlResult

    @Query("SELECT * FROM ml_results WHERE swimmerId = :swimmerId AND sessionId = :sessionId LIMIT 1")
    suspend fun getBySwimmerAndSessionId(swimmerId: Int, sessionId: Int): MlResult?

    @Update
    suspend fun update(mlResult: MlResult)

    @Query("DELETE FROM ml_results")
    fun clearAll()
}
