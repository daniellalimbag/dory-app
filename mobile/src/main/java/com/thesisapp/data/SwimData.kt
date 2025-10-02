package com.thesisapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "swim_data")
data class SwimData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val timestamp: Long = System.currentTimeMillis(),

    // Accelerometer
    val accel_x: Float? = null,
    val accel_y: Float? = null,
    val accel_z: Float? = null,

    // Gyroscope
    val gyro_x: Float? = null,
    val gyro_y: Float? = null,
    val gyro_z: Float? = null,

    // Heart Rate
    val heart_rate: Float? = null,
    val ppg: Float? = null,
    val ecg: Float? = null
)