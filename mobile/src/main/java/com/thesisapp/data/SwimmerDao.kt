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

    @Insert
    fun insertSwimmer(swimmer: Swimmer)

    @Update
    fun updateSwimmer(swimmer: Swimmer)

    @Delete
    fun deleteSwimmer(swimmer: Swimmer)

    @Query("DELETE FROM swimmers")
    fun clearAll()
}