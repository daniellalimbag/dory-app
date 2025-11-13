package com.thesisapp.data

import androidx.room.*

@Dao
interface GoalDao {
    
    @Insert
    suspend fun insert(goal: Goal): Long
    
    @Update
    suspend fun update(goal: Goal)
    
    @Delete
    suspend fun delete(goal: Goal)
    
    @Query("SELECT * FROM goals WHERE id = :goalId")
    suspend fun getById(goalId: Int): Goal?
    
    @Query("SELECT * FROM goals WHERE swimmerId = :swimmerId AND teamId = :teamId AND isActive = 1 LIMIT 1")
    suspend fun getActiveGoalForSwimmer(swimmerId: Int, teamId: Int): Goal?
    
    @Query("SELECT * FROM goals WHERE swimmerId = :swimmerId AND teamId = :teamId")
    suspend fun getAllGoalsForSwimmer(swimmerId: Int, teamId: Int): List<Goal>
    
    @Query("UPDATE goals SET isActive = 0 WHERE id = :goalId")
    suspend fun deactivateGoal(goalId: Int)
}
