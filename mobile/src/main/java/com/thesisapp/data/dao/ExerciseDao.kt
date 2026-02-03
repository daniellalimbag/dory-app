package com.thesisapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.ExerciseCategory

@Dao
interface ExerciseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exercise: Exercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(exercises: List<Exercise>)

    @Update
    suspend fun update(exercise: Exercise)

    @Delete
    suspend fun delete(exercise: Exercise)

    @Query("SELECT * FROM exercises WHERE teamId = :teamId ORDER BY name ASC")
    suspend fun getExercisesForTeam(teamId: Int): List<Exercise>

    @Query("SELECT * FROM exercises WHERE teamId = :teamId AND category = :category ORDER BY name ASC")
    suspend fun getExercisesByCategory(teamId: Int, category: ExerciseCategory): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Int): Exercise?

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getExerciseById(id: Int): Exercise?

    @Query("DELETE FROM exercises WHERE teamId = :teamId")
    suspend fun deleteAllForTeam(teamId: Int)

    @Query("DELETE FROM exercises")
    suspend fun clearAll()
}