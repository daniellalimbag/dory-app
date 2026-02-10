package com.thesisapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thesisapp.data.non_dao.GoalProgress

@Dao
interface GoalProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: GoalProgress): Long

    @Update
    suspend fun update(progress: GoalProgress)

    @Delete
    suspend fun delete(progress: GoalProgress)

    @Query("SELECT * FROM goal_progress WHERE goalId = :goalId ORDER BY date ASC")
    suspend fun getProgressForGoal(goalId: Int): List<GoalProgress>

    @Query("SELECT * FROM goal_progress WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): GoalProgress?

    @Query("DELETE FROM goal_progress WHERE goalId = :goalId")
    suspend fun deleteAllProgressForGoal(goalId: Int)
}