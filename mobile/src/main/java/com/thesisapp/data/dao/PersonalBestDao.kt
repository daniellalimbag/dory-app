package com.thesisapp.data.dao

import androidx.room.*
import com.thesisapp.data.non_dao.PersonalBest
import com.thesisapp.data.non_dao.StrokeType

@Dao
interface PersonalBestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(personalBest: PersonalBest): Long

    @Update
    suspend fun update(personalBest: PersonalBest)

    @Delete
    suspend fun delete(personalBest: PersonalBest)

    @Query("SELECT * FROM personal_bests WHERE swimmerId = :swimmerId ORDER BY distance, strokeType")
    suspend fun getBySwimmerId(swimmerId: Int): List<PersonalBest>

    @Query("SELECT * FROM personal_bests WHERE swimmerId = :swimmerId AND distance = :distance AND strokeType = :strokeType LIMIT 1")
    suspend fun getBySwimmerDistanceStroke(swimmerId: Int, distance: Int, strokeType: StrokeType): PersonalBest?

    @Query("SELECT * FROM personal_bests WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): PersonalBest?

    @Query("DELETE FROM personal_bests WHERE swimmerId = :swimmerId")
    suspend fun deleteBySwimmerId(swimmerId: Int)

    @Query("DELETE FROM personal_bests")
    suspend fun clearAll()
}
