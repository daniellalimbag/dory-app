package com.thesisapp.communication

import com.thesisapp.data.SwimData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionChunkPayload(
    val sessionId: Int,
    val chunkIndex: Int,
    val totalChunks: Int,
    val records: List<SessionChunkRecord> = emptyList()
)

@Serializable
data class SessionChunkRecord(
    val timestamp: Long? = null,
    @SerialName("accel_x") val accelX: Float? = null,
    @SerialName("accel_y") val accelY: Float? = null,
    @SerialName("accel_z") val accelZ: Float? = null,
    @SerialName("gyro_x") val gyroX: Float? = null,
    @SerialName("gyro_y") val gyroY: Float? = null,
    @SerialName("gyro_z") val gyroZ: Float? = null,
    @SerialName("heart_rate") val heartRate: Float? = null,
    val ppg: Float? = null,
    val ecg: Float? = null
) {
    fun toSwimData(sessionId: Int): SwimData {
        val safeTimestamp = timestamp?.takeIf { it > 0 } ?: System.currentTimeMillis()
        return SwimData(
            sessionId = sessionId,
            timestamp = safeTimestamp,
            accel_x = accelX,
            accel_y = accelY,
            accel_z = accelZ,
            gyro_x = gyroX,
            gyro_y = gyroY,
            gyro_z = gyroZ,
            heart_rate = heartRate,
            ppg = ppg,
            ecg = ecg
        )
    }
}