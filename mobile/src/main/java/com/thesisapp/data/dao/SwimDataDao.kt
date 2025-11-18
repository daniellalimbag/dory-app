package com.thesisapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thesisapp.data.non_dao.SwimData

@Dao
interface SwimDataDao {
    @Insert
    fun insert(swimData: SwimData)

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertAll(swimData: List<SwimData>)

    @Query("SELECT * FROM swim_data WHERE timestamp BETWEEN :fromDate AND :toDate")
    fun getSwimsBetweenDates(fromDate: Long, toDate: Long): List<SwimData>

    @Query("SELECT * FROM swim_data WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSwimDataForSession(sessionId: Int): List<SwimData>

    @Query("SELECT COUNT(DISTINCT sessionId) FROM swim_data WHERE timestamp BETWEEN :fromDate AND :toDate")
    fun countSessionsBetweenDates(fromDate: Long, toDate: Long): Int

    @Query("SELECT timestamp FROM swim_data WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT 1")
    fun getFirstTimestampForSession(sessionId: Int): Long?

    @Query("DELETE FROM swim_data")
    fun clearAll()
}