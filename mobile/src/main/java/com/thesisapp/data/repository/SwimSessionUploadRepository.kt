package com.thesisapp.data.repository

import android.util.Log
import com.thesisapp.data.dao.MlResultDao
import com.thesisapp.data.dao.SwimDataDao
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.data.non_dao.SwimData
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SwimSessionUploadRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val mlResultDao: MlResultDao,
    private val swimDataDao: SwimDataDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun uploadSession(sessionId: Int, includeSamples: Boolean) {
        withContext(Dispatchers.IO) {
            Log.d("DEBUG", "uploadSession(sessionId=$sessionId, includeSamples=$includeSamples)")

            val session = mlResultDao.getBySessionId(sessionId)
                ?: error("No local session found for sessionId=$sessionId")

            val resolvedRemoteSwimmerId = resolveRemoteSwimmerIdForCurrentAuthUserOrNull()

            upsertRemoteSession(session, resolvedRemoteSwimmerId)

            if (includeSamples) {
                val samples = swimDataDao.getSwimDataForSession(sessionId)
                if (samples.isEmpty()) {
                    Log.d("DEBUG", "No samples to upload for sessionId=$sessionId")
                    return@withContext
                }

                // Idempotency: replace all samples for this session on the server.
                supabase.from("swim_data").delete {
                    filter { eq("session_id", sessionId) }
                }

                samples.chunked(500).forEachIndexed { idx, chunk ->
                    Log.d("DEBUG", "Uploading swim_data chunk ${idx + 1}/${(samples.size + 499) / 500} for sessionId=$sessionId")
                    val payload = chunk.map { it.toRemotePayload() }
                    supabase.from("swim_data").insert(payload)
                }
            }
        }
    }

    private suspend fun upsertRemoteSession(session: MlResult, resolvedRemoteSwimmerId: Long?) {
        val payload = buildJsonObject {
            put("session_id", session.sessionId)
            put("swimmer_id", resolvedRemoteSwimmerId ?: session.swimmerId)
            session.exerciseId?.let { put("exercise_id", it) }
            put("date", session.date)
            put("time_start", session.timeStart)
            put("time_end", session.timeEnd)
            session.exerciseName?.let { put("exercise_name", it) }
            session.distance?.let { put("distance", it) }
            session.sets?.let { put("sets", it) }
            session.reps?.let { put("reps", it) }
            session.effortLevel?.let { put("effort_level", it) }
            session.energyZone?.let { put("energy_zone", it) }
            session.seasonPhase?.let { put("season_phase", it) }
            session.strokeCount?.let { put("stroke_count", it) }
            session.avgStrokeLength?.let { put("avg_stroke_length", it) }
            session.strokeIndex?.let { put("stroke_index", it) }
            session.avgLapTime?.let { put("avg_lap_time", it) }
            session.totalDistance?.let { put("total_distance", it) }
            session.heartRateBefore?.let { put("heart_rate_before", it) }
            session.heartRateAfter?.let { put("heart_rate_after", it) }
            session.avgHeartRate?.let { put("avg_heart_rate", it) }
            session.maxHeartRate?.let { put("max_heart_rate", it) }
            put("backstroke", session.backstroke)
            put("breaststroke", session.breaststroke)
            put("butterfly", session.butterfly)
            put("freestyle", session.freestyle)
            put("notes", session.notes)
        }

        val existingJson = supabase.from("swim_sessions").select {
            filter { eq("session_id", session.sessionId) }
            limit(1)
        }.data

        val exists = runCatching {
            existingJson.trim().startsWith("[") && existingJson != "[]"
        }.getOrDefault(false)

        if (exists) {
            supabase.from("swim_sessions").update(payload) {
                filter { eq("session_id", session.sessionId) }
            }
        } else {
            supabase.from("swim_sessions").insert(payload)
        }
    }

    @kotlinx.serialization.Serializable
    private data class RemoteSwimmerIdRow(
        val id: Long
    )

    private suspend fun resolveRemoteSwimmerIdForCurrentAuthUserOrNull(): Long? {
        val authUserId = supabase.auth.currentUserOrNull()?.id ?: return null

        val swimmerJson = supabase.from("swimmers").select {
            filter { eq("user_id", authUserId) }
            limit(1)
        }.data

        return runCatching {
            json.decodeFromString<List<RemoteSwimmerIdRow>>(swimmerJson).firstOrNull()?.id
        }.getOrNull()
    }

    private fun SwimData.toRemotePayload() = buildJsonObject {
        put("session_id", sessionId)
        put("timestamp", timestamp)
        accel_x?.let { put("accel_x", it) }
        accel_y?.let { put("accel_y", it) }
        accel_z?.let { put("accel_z", it) }
        gyro_x?.let { put("gyro_x", it) }
        gyro_y?.let { put("gyro_y", it) }
        gyro_z?.let { put("gyro_z", it) }
        heart_rate?.let { put("heart_rate", it) }
        ppg?.let { put("ppg", it) }
        ecg?.let { put("ecg", it) }
    }
}
