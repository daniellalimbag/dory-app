package com.thesisapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SensorDataDao {
    @Insert
    suspend fun insertSensorData(sensorData: SensorData)

    @Query("SELECT * FROM sensor_data ORDER BY timestamp DESC")
    suspend fun getAllSensorData(): List<SensorData>

    @Query("SELECT * FROM sensor_data WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getSensorDataForSession(sessionId: Int): List<SensorData>

    @Query("DELETE FROM sensor_data WHERE sessionId = :sessionId")
    suspend fun deleteSensorDataForSession(sessionId: Int)
}