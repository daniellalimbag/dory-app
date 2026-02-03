package com.thesisapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thesisapp.data.non_dao.Coach

@Dao
interface CoachDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertCoach(coach: Coach): Long

    @Query("SELECT * FROM coaches WHERE userId = :userId")
    suspend fun getByUserId(userId: String): Coach?
}
