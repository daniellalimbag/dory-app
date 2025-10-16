package com.thesisapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete

@Dao
interface SwimmerDao {
    @Query("SELECT * FROM swimmers")
    fun getAllSwimmers(): List<Swimmer>

    @Query("SELECT * FROM swimmers WHERE teamId = :teamId")
    fun getSwimmersForTeam(teamId: Int): List<Swimmer>

    @Query("SELECT * FROM swimmers WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Swimmer?

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