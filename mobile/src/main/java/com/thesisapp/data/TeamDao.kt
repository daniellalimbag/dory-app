package com.thesisapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update

@Dao
interface TeamDao {
    @Insert
    suspend fun insert(team: Team): Long

    @Update
    suspend fun update(team: Team)

    @Delete
    suspend fun delete(team: Team)

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun getById(id: Int): Team?

    @Query("SELECT * FROM teams WHERE joinCode = :code LIMIT 1")
    suspend fun getByJoinCode(code: String): Team?

    @Query("SELECT * FROM teams")
    suspend fun getAll(): List<Team>
}

