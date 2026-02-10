package com.thesisapp.data.repository

import android.util.Log
import com.thesisapp.data.dao.PersonalBestDao
import com.thesisapp.data.non_dao.PersonalBest
import com.thesisapp.data.non_dao.StrokeType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalBestSyncRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val personalBestDao: PersonalBestDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RemotePersonalBestRow(
        val id: Int,
        @SerialName("swimmer_id") val swimmerId: Int,
        val distance: Int,
        @SerialName("stroke_type") val strokeType: String,
        @SerialName("best_time") val bestTime: Float,
        @SerialName("updated_at") val updatedAt: String? = null
    )

    suspend fun pullPersonalBests(swimmerId: Int) {
        withContext(Dispatchers.IO) {
            val rowsJson = supabase.from("personal_bests").select {
                filter { eq("swimmer_id", swimmerId) }
            }.data

            val rows = runCatching {
                json.decodeFromString<List<RemotePersonalBestRow>>(rowsJson)
            }.getOrElse { e ->
                Log.e("PB-Sync", "Failed decoding personal_bests", e)
                return@withContext
            }

            personalBestDao.deleteBySwimmerId(swimmerId)

            rows.forEach { r ->
                val strokeEnum = runCatching { StrokeType.valueOf(r.strokeType) }
                    .getOrElse { StrokeType.FREESTYLE }

                val updatedAtMillis = r.updatedAt?.let { parseIsoToMillisOrNull(it) } ?: System.currentTimeMillis()

                personalBestDao.insert(
                    PersonalBest(
                        id = r.id,
                        swimmerId = r.swimmerId,
                        distance = r.distance,
                        strokeType = strokeEnum,
                        bestTime = r.bestTime,
                        updatedAt = updatedAtMillis
                    )
                )
            }
        }
    }

    suspend fun pushPersonalBest(pb: PersonalBest) {
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("swimmer_id", pb.swimmerId)
                put("distance", pb.distance)
                put("stroke_type", pb.strokeType.name)
                put("best_time", pb.bestTime)
            }

            try {
                supabase.from("personal_bests").insert(payload)
            } catch (e: Exception) {
                // If it already exists (unique: swimmer_id + distance + stroke_type), update instead.
                if (e.message?.contains("duplicate", ignoreCase = true) == true ||
                    e.message?.contains("unique", ignoreCase = true) == true
                ) {
                    supabase.from("personal_bests").update(payload) {
                        filter {
                            eq("swimmer_id", pb.swimmerId)
                            eq("distance", pb.distance)
                            eq("stroke_type", pb.strokeType.name)
                        }
                    }
                } else {
                    throw e
                }
            }
        }
    }

    suspend fun deletePersonalBest(pb: PersonalBest) {
        withContext(Dispatchers.IO) {
            supabase.from("personal_bests").delete {
                filter {
                    eq("swimmer_id", pb.swimmerId)
                    eq("distance", pb.distance)
                    eq("stroke_type", pb.strokeType.name)
                }
            }
        }
    }

    private fun parseIsoToMillisOrNull(value: String): Long? {
        return value.toLongOrNull() ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }
}
