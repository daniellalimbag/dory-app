package com.thesisapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import java.util.Date

// Data class for joined swimmer and swim data
data class SwimmerWithSwimData(
    val swimmerId: Int,
    val swimmerName: String,
    val swimmerAge: Int,
    val swimmerWingspan: Float,
    val swimmerCategory: String,
    val swimDataId: Int,
    val sessionId: Int,
    val timestamp: Long,
    val accel_x: Float?,
    val accel_y: Float?,
    val accel_z: Float?,
    val gyro_x: Float?,
    val gyro_y: Float?,
    val gyro_z: Float?,
    val heart_rate: Float?,
    val ppg: Float?,
    val ecg: Float?
)

@Dao
interface SwimDataDao {
    @Insert
    fun insert(swimData: SwimData)

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

    // Join queries to get swimmer data with swim data
    @Query("""
        SELECT 
            s.id as swimmerId,
            s.name as swimmerName,
            s.age as swimmerAge,
            s.wingspan as swimmerWingspan,
            s.category as swimmerCategory,
            sd.id as swimDataId,
            sd.sessionId,
            sd.timestamp,
            sd.accel_x,
            sd.accel_y,
            sd.accel_z,
            sd.gyro_x,
            sd.gyro_y,
            sd.gyro_z,
            sd.heart_rate,
            sd.ppg,
            sd.ecg
        FROM swim_data sd
        INNER JOIN swimmers s ON sd.swimmerId = s.id
        WHERE sd.swimmerId = :swimmerId
        ORDER BY sd.timestamp ASC
    """)
    suspend fun getSwimDataWithSwimmer(swimmerId: Int): List<SwimmerWithSwimData>

    @Query("""
        SELECT 
            s.id as swimmerId,
            s.name as swimmerName,
            s.age as swimmerAge,
            s.wingspan as swimmerWingspan,
            s.category as swimmerCategory,
            sd.id as swimDataId,
            sd.sessionId,
            sd.timestamp,
            sd.accel_x,
            sd.accel_y,
            sd.accel_z,
            sd.gyro_x,
            sd.gyro_y,
            sd.gyro_z,
            sd.heart_rate,
            sd.ppg,
            sd.ecg
        FROM swim_data sd
        INNER JOIN swimmers s ON sd.swimmerId = s.id
        WHERE sd.sessionId = :sessionId
        ORDER BY sd.timestamp ASC
    """)
    suspend fun getSessionDataWithSwimmer(sessionId: Int): List<SwimmerWithSwimData>

    @Query("""
        SELECT 
            s.id as swimmerId,
            s.name as swimmerName,
            s.age as swimmerAge,
            s.wingspan as swimmerWingspan,
            s.category as swimmerCategory,
            sd.id as swimDataId,
            sd.sessionId,
            sd.timestamp,
            sd.accel_x,
            sd.accel_y,
            sd.accel_z,
            sd.gyro_x,
            sd.gyro_y,
            sd.gyro_z,
            sd.heart_rate,
            sd.ppg,
            sd.ecg
        FROM swim_data sd
        INNER JOIN swimmers s ON sd.swimmerId = s.id
        WHERE sd.timestamp BETWEEN :fromDate AND :toDate
        ORDER BY sd.timestamp ASC
    """)
    suspend fun getSwimDataWithSwimmerBetweenDates(fromDate: Long, toDate: Long): List<SwimmerWithSwimData>
}