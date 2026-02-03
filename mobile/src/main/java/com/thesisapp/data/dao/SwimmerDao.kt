package com.thesisapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.thesisapp.data.non_dao.Swimmer

@Dao
interface SwimmerDao {
    @Query("SELECT * FROM swimmers")
    suspend fun getAllSwimmers(): List<Swimmer>

    // Team-based queries moved to TeamMembershipDao
    // Use teamMembershipDao.getSwimmersForTeam(teamId) instead

    @Query("SELECT * FROM swimmers WHERE id = :id")
    suspend fun getById(id: Int): Swimmer?

    @Query("SELECT * FROM swimmers WHERE userId = :userId")
    suspend fun getByUserId(userId: String): Swimmer?

    @Query("UPDATE swimmers SET userId = :userId WHERE id = :swimmerId")
    suspend fun setUserIdForSwimmer(swimmerId: Int, userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwimmer(swimmer: Swimmer): Long

    @Update
    suspend fun updateSwimmer(swimmer: Swimmer)

    @Delete
    suspend fun deleteSwimmer(swimmer: Swimmer)

    @Query("DELETE FROM swimmers")
    suspend fun clearAll()
}