package com.thesisapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thesisapp.data.non_dao.Coach

@Dao
interface CoachDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCoach(coach: Coach): Long

    @Update
    suspend fun updateCoach(coach: Coach)

    @Query("SELECT * FROM coaches WHERE userId = :userId")
    suspend fun getByUserId(userId: String): Coach?
}
