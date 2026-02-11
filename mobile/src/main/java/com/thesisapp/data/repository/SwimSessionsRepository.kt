package com.thesisapp.data.repository

import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.data.non_dao.SwimData
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwimSessionsRepository @Inject constructor(
    private val supabase: SupabaseClient
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RemoteSessionRow(
        @SerialName("session_id") val sessionId: Int,
        @SerialName("swimmer_id") val swimmerId: Int,
        @SerialName("exercise_id") val exerciseId: Int? = null,
        val date: String,
        @SerialName("time_start") val timeStart: String,
        @SerialName("time_end") val timeEnd: String,
        @SerialName("exercise_name") val exerciseName: String? = null,
        val distance: Int? = null,
        val sets: Int? = null,
        val reps: Int? = null,
        @SerialName("effort_level") val effortLevel: String? = null,
        @SerialName("energy_zone") val energyZone: String? = null,
        @SerialName("season_phase") val seasonPhase: String? = null,
        @SerialName("stroke_count") val strokeCount: Int? = null,
        @SerialName("avg_stroke_length") val avgStrokeLength: Float? = null,
        @SerialName("stroke_index") val strokeIndex: Float? = null,
        @SerialName("avg_lap_time") val avgLapTime: Float? = null,
        @SerialName("total_distance") val totalDistance: Int? = null,
        @SerialName("heart_rate_before") val heartRateBefore: Int? = null,
        @SerialName("heart_rate_after") val heartRateAfter: Int? = null,
        @SerialName("avg_heart_rate") val avgHeartRate: Int? = null,
        @SerialName("max_heart_rate") val maxHeartRate: Int? = null,
        val backstroke: Float? = null,
        val breaststroke: Float? = null,
        val butterfly: Float? = null,
        val freestyle: Float? = null,
        val notes: String? = null
    )

    @Serializable
    private data class RemoteSwimDataRow(
        @SerialName("session_id") val sessionId: Int,
        val timestamp: Long,
        val accel_x: Float? = null,
        val accel_y: Float? = null,
        val accel_z: Float? = null,
        val gyro_x: Float? = null,
        val gyro_y: Float? = null,
        val gyro_z: Float? = null,
        @SerialName("heart_rate") val heartRate: Float? = null,
        val ppg: Float? = null,
        val ecg: Float? = null
    )

    suspend fun getSessionsForSwimmer(swimmerId: Long): List<MlResult> {
        return withContext(Dispatchers.IO) {
            val sessionsJson = supabase.from("swim_sessions").select {
                filter { eq("swimmer_id", swimmerId) }
            }.data

            val rows = json.decodeFromString<List<RemoteSessionRow>>(sessionsJson)
            rows.map { it.toMlResult() }
        }
    }

    suspend fun getSessionById(sessionId: Int): MlResult? {
        return withContext(Dispatchers.IO) {
            val sessionJson = supabase.from("swim_sessions").select {
                filter { eq("session_id", sessionId) }
                limit(1)
            }.data

            json.decodeFromString<List<RemoteSessionRow>>(sessionJson).firstOrNull()?.toMlResult()
        }
    }

    suspend fun getSwimDataForSession(sessionId: Int): List<SwimData> {
        return withContext(Dispatchers.IO) {
            val allRows = mutableListOf<RemoteSwimDataRow>()
            var offset = 0L
            val batchSize = 1000L
            
            // Fetch all data in batches to bypass the 1000 row limit
            while (true) {
                val dataJson = supabase.from("swim_data").select {
                    filter { eq("session_id", sessionId) }
                    range(offset until (offset + batchSize))
                }.data

                val rows = json.decodeFromString<List<RemoteSwimDataRow>>(dataJson)
                if (rows.isEmpty()) break
                
                allRows.addAll(rows)
                
                // If we got fewer rows than the batch size, we've reached the end
                if (rows.size < batchSize.toInt()) break
                
                offset += batchSize
            }
            
            android.util.Log.d("SwimSessionsRepo", "Fetched ${allRows.size} total swim_data rows for session $sessionId")
            
            allRows.map { r ->
                SwimData(
                    sessionId = r.sessionId,
                    timestamp = r.timestamp,
                    accel_x = r.accel_x,
                    accel_y = r.accel_y,
                    accel_z = r.accel_z,
                    gyro_x = r.gyro_x,
                    gyro_y = r.gyro_y,
                    gyro_z = r.gyro_z,
                    heart_rate = r.heartRate,
                    ppg = r.ppg,
                    ecg = r.ecg
                )
            }.sortedBy { it.timestamp }
        }
    }

    private fun RemoteSessionRow.toMlResult(): MlResult {
        return MlResult(
            sessionId = sessionId,
            swimmerId = swimmerId,
            exerciseId = exerciseId,
            date = date,
            timeStart = timeStart,
            timeEnd = timeEnd,
            exerciseName = exerciseName,
            distance = distance,
            sets = sets,
            reps = reps,
            effortLevel = effortLevel,
            energyZone = energyZone,
            seasonPhase = seasonPhase,
            strokeCount = strokeCount,
            avgStrokeLength = avgStrokeLength,
            strokeIndex = strokeIndex,
            avgLapTime = avgLapTime,
            totalDistance = totalDistance,
            heartRateBefore = heartRateBefore,
            heartRateAfter = heartRateAfter,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            backstroke = backstroke ?: 0f,
            breaststroke = breaststroke ?: 0f,
            butterfly = butterfly ?: 0f,
            freestyle = freestyle ?: 0f,
            notes = notes ?: ""
        )
    }
}
