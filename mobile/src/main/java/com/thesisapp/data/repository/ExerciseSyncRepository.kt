package com.thesisapp.data.repository

import com.thesisapp.data.dao.ExerciseDao
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.ExerciseCategory
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
class ExerciseSyncRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val exerciseDao: ExerciseDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RemoteExerciseRow(
        val id: Int,
        @SerialName("team_id") val teamId: Long,
        val name: String,
        val category: String,
        val description: String? = null,
        val distance: Int? = null,
        val sets: Int? = null,
        @SerialName("rest_time") val restTime: Int? = null,
        @SerialName("effort_level") val effortLevel: Int? = null,
        @SerialName("created_at") val createdAt: Long? = null
    )

    suspend fun syncExercises(teamId: Int) {
        withContext(Dispatchers.IO) {
            val exercisesJson = supabase.from("exercises").select {
                filter { eq("team_id", teamId) }
            }.data

            val remoteExercises = json.decodeFromString<List<RemoteExerciseRow>>(exercisesJson)

            val mapped = remoteExercises.map { re ->
                val categoryEnum = runCatching { ExerciseCategory.valueOf(re.category) }
                    .getOrElse { ExerciseCategory.SPRINT }

                Exercise(
                    id = re.id,
                    teamId = re.teamId.toInt(),
                    name = re.name,
                    category = categoryEnum,
                    description = re.description,
                    distance = re.distance,
                    sets = re.sets,
                    restTime = re.restTime,
                    effortLevel = re.effortLevel,
                    createdAt = re.createdAt ?: System.currentTimeMillis()
                )
            }

            exerciseDao.upsertAll(mapped)
        }
    }
}
