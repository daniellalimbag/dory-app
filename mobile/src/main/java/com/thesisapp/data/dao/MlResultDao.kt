package com.thesisapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thesisapp.data.non_dao.MlResult

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

    @Query("SELECT * FROM ml_results WHERE swimmerId = :swimmerId ORDER BY date DESC, timeStart DESC")
    suspend fun getResultsForSwimmer(swimmerId: Int): List<MlResult>

    @Update
    suspend fun update(mlResult: MlResult)

    @Query("DELETE FROM ml_results")
    fun clearAll()
}