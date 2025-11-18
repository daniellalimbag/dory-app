package com.thesisapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thesisapp.data.non_dao.Goal

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