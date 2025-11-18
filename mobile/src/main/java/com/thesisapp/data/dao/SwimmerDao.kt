package com.thesisapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.thesisapp.data.non_dao.Swimmer

@Dao
interface SwimmerDao {
    @Query("SELECT * FROM swimmers")
    fun getAllSwimmers(): List<Swimmer>

    // Team-based queries moved to TeamMembershipDao
    // Use teamMembershipDao.getSwimmersForTeam(teamId) instead

    @Query("SELECT * FROM swimmers WHERE id = :id")
    suspend fun getById(id: Int): Swimmer?

    @Insert
    fun insertSwimmer(swimmer: Swimmer): Long

    @Update
    fun updateSwimmer(swimmer: Swimmer)

    @Delete
    fun deleteSwimmer(swimmer: Swimmer)

    @Query("DELETE FROM swimmers")
    fun clearAll()
}