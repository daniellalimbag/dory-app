package com.thesisapp.data.repository

import android.util.Log
import com.thesisapp.data.dao.MlResultDao
import com.thesisapp.data.dao.SwimDataDao
import com.thesisapp.data.dao.SwimmerDao
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.data.non_dao.SwimData
import com.thesisapp.utils.MetricsApiClient
import com.thesisapp.utils.MetricsSample
import com.thesisapp.utils.MetricsSessionRequest
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
    private val swimDataDao: SwimDataDao,
    private val swimmerDao: SwimmerDao
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

                runCatching {
                    val metrics = computeMetricsFromSamples(session = session, samples = samples)
                    if (metrics != null) {
                        val updatedLocal = session.copy(
                            strokeCount = metrics.strokeCount,
                            avgStrokeLength = metrics.avgStrokeLength,
                            strokeIndex = metrics.strokeIndex,
                            avgLapTime = metrics.avgLapTime,
                            totalDistance = metrics.totalDistance
                        )

                        mlResultDao.update(updatedLocal)

                        val metricsPayload = buildJsonObject {
                            metrics.strokeCount?.let { put("stroke_count", it) }
                            metrics.avgStrokeLength?.let { put("avg_stroke_length", it) }
                            metrics.strokeIndex?.let { put("stroke_index", it) }
                            metrics.avgLapTime?.let { put("avg_lap_time", it) }
                            metrics.totalDistance?.let { put("total_distance", it) }
                        }

                        supabase.from("swim_sessions").update(metricsPayload) {
                            filter { eq("session_id", session.sessionId) }
                        }
                    }
                }.onFailure { e ->
                    Log.d("DEBUG", "Metrics pipeline call failed", e)
                    throw IllegalStateException("Metrics pipeline failed: ${e.message}")
                }
            }
        }
    }

    private data class ComputedSessionMetrics(
        val strokeCount: Int?,
        val avgStrokeLength: Float?,
        val strokeIndex: Float?,
        val avgLapTime: Float?,
        val totalDistance: Int?
    )

    private suspend fun computeMetricsFromSamples(
        session: MlResult,
        samples: List<SwimData>
    ): ComputedSessionMetrics? {
        val metricsSamples = samples.mapNotNull { s ->
            val ax = s.accel_x ?: return@mapNotNull null
            val ay = s.accel_y ?: return@mapNotNull null
            val az = s.accel_z ?: return@mapNotNull null
            val gx = s.gyro_x ?: return@mapNotNull null
            val gy = s.gyro_y ?: return@mapNotNull null
            val gz = s.gyro_z ?: return@mapNotNull null

            MetricsSample(
                timestamp_ms = s.timestamp,
                accel_x = ax.toDouble(),
                accel_y = ay.toDouble(),
                accel_z = az.toDouble(),
                gyro_x = gx.toDouble(),
                gyro_y = gy.toDouble(),
                gyro_z = gz.toDouble(),
                stroke_type = null
            )
        }

        if (metricsSamples.isEmpty()) return null

        val req = MetricsSessionRequest(
            session_id = session.sessionId,
            swimmer_id = session.swimmerId,
            exercise_id = session.exerciseId,
            pool_length_m = 50.0,
            samples = metricsSamples
        )

        Log.d("DEBUG", "Calling metrics API for sessionId=${session.sessionId}, sampleCount=${metricsSamples.size}")
        val resp = MetricsApiClient.service.computeMetrics(req)

        val avg = resp.session_averages
        Log.d(
            "DEBUG",
            "Metrics API success: lapCount=${avg.lap_count}, strokeCount=${avg.stroke_count}, avgStrokeLength=${avg.avg_stroke_length_m}, avgStrokeIndex=${avg.avg_stroke_index}, avgLapTime=${avg.avg_lap_time_s}"
        )
        val strokeCountInt = avg.stroke_count.toInt()
        val lapCount = avg.lap_count

        return ComputedSessionMetrics(
            strokeCount = strokeCountInt,
            avgStrokeLength = avg.avg_stroke_length_m.toFloat(),
            strokeIndex = avg.avg_stroke_index.toFloat(),
            avgLapTime = avg.avg_lap_time_s.toFloat(),
            totalDistance = (50.0 * lapCount).toInt()
        )
    }

    private suspend fun upsertRemoteSession(session: MlResult, resolvedRemoteSwimmerId: Long?) {
        val effectiveRemoteSwimmerId = resolvedRemoteSwimmerId
            ?: resolveRemoteSwimmerIdForSessionOrNull(session)
            ?: error("Could not resolve remote swimmer_id for sessionId=${session.sessionId}. Please re-login as the swimmer and try again.")

        val payload = buildJsonObject {
            put("session_id", session.sessionId)
            put("swimmer_id", effectiveRemoteSwimmerId)
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

        try {
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
        } catch (e: Exception) {
            // If insert fails due to duplicate key, try update instead
            if (e.message?.contains("duplicate key") == true || e.message?.contains("unique constraint") == true) {
                Log.d("DEBUG", "Duplicate session_id detected, updating instead")
                supabase.from("swim_sessions").update(payload) {
                    filter { eq("session_id", session.sessionId) }
                }
            } else {
                throw e
            }
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

    private suspend fun resolveRemoteSwimmerIdForSessionOrNull(session: MlResult): Long? {
        val localSwimmerUserId = runCatching {
            swimmerDao.getById(session.swimmerId)?.userId
        }.getOrNull()

        if (localSwimmerUserId.isNullOrBlank()) return null

        val swimmerJson = supabase.from("swimmers").select {
            filter { eq("user_id", localSwimmerUserId) }
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
