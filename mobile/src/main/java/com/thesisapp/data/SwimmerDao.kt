package com.thesisapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SwimmerDao {
    @Query("SELECT * FROM swimmers")
    fun getAllSwimmers(): List<Swimmer>

    @Query("SELECT * FROM swimmers WHERE id = :id")
    suspend fun getSwimmerById(id: Int): Swimmer?

    @Insert
    fun insertSwimmer(swimmer: Swimmer)

    @Query("DELETE FROM swimmers")
    fun clearAll()
}