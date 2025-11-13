package com.thesisapp.data

import androidx.room.*

@Dao
interface GoalProgressDao {
    
    @Insert
    suspend fun insert(progress: GoalProgress): Long
    
    @Update
    suspend fun update(progress: GoalProgress)
    
    @Delete
    suspend fun delete(progress: GoalProgress)
    
    @Query("SELECT * FROM goal_progress WHERE goalId = :goalId ORDER BY date ASC")
    suspend fun getProgressForGoal(goalId: Int): List<GoalProgress>
    
    @Query("DELETE FROM goal_progress WHERE goalId = :goalId")
    suspend fun deleteAllProgressForGoal(goalId: Int)
}
