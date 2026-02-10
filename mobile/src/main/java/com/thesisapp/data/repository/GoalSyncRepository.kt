package com.thesisapp.data.repository

import android.util.Log
import com.thesisapp.data.dao.GoalDao
import com.thesisapp.data.dao.GoalProgressDao
import com.thesisapp.data.non_dao.Goal
import com.thesisapp.data.non_dao.GoalProgress
import com.thesisapp.data.non_dao.GoalType
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
class GoalSyncRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val goalDao: GoalDao,
    private val goalProgressDao: GoalProgressDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RemoteGoalRow(
        val id: Int,
        @SerialName("client_id") val clientId: String,
        @SerialName("swimmer_id") val swimmerId: Int,
        @SerialName("team_id") val teamId: Int,
        @SerialName("event_name") val eventName: String,
        @SerialName("goal_time") val goalTime: String,
        @SerialName("start_date") val startDate: String,
        @SerialName("end_date") val endDate: String,
        @SerialName("goal_type") val goalType: String,
        @SerialName("is_active") val isActive: Boolean = true
    )

    @Serializable
    private data class RemoteGoalProgressRow(
        val id: Int,
        @SerialName("client_id") val clientId: String,
        @SerialName("goal_id") val goalId: Int,
        val date: String,
        @SerialName("projected_race_time") val projectedRaceTime: String,
        @SerialName("session_id") val sessionId: Int? = null
    )

    suspend fun pullGoalsAndProgress(swimmerId: Int, teamId: Int) {
        withContext(Dispatchers.IO) {
            val goalsJson = supabase.from("goals").select {
                filter {
                    eq("swimmer_id", swimmerId)
                    eq("team_id", teamId)
                }
            }.data

            val remoteGoals = runCatching {
                json.decodeFromString<List<RemoteGoalRow>>(goalsJson)
            }.getOrElse { e ->
                Log.e("Goal-Sync", "Failed decoding goals", e)
                return@withContext
            }

            // Remote is source of truth for this swimmer/team.
            // We reconcile by clientId so primary keys can differ.
            val remoteClientIds = remoteGoals.map { it.clientId }.toSet()
            val localGoals = goalDao.getAllGoalsForSwimmer(swimmerId, teamId)
            localGoals.filter { it.clientId.isNotBlank() && it.clientId !in remoteClientIds }
                .forEach { goalDao.delete(it) }

            remoteGoals.forEach { g ->
                val goalTypeEnum = runCatching { GoalType.valueOf(g.goalType) }
                    .getOrElse { GoalType.SPRINT }

                val existingLocal = goalDao.getByClientId(g.clientId)
                val localId = existingLocal?.id ?: 0

                val mapped = Goal(
                    id = localId,
                    clientId = g.clientId,
                    swimmerId = g.swimmerId,
                    teamId = g.teamId,
                    eventName = g.eventName,
                    goalTime = g.goalTime,
                    startDate = parseIsoToMillisOrNow(g.startDate),
                    endDate = parseIsoToMillisOrNow(g.endDate),
                    goalType = goalTypeEnum,
                    isActive = g.isActive
                )

                val insertedId = goalDao.insertOrReplace(mapped).toInt()
                val resolvedLocalGoalId = if (localId == 0) insertedId else localId

                // Pull progress points for this goal
                val progressJson = supabase.from("goal_progress").select {
                    filter { eq("goal_id", g.id) }
                }.data

                val remoteProgress = runCatching {
                    json.decodeFromString<List<RemoteGoalProgressRow>>(progressJson)
                }.getOrElse { emptyList() }

                goalProgressDao.deleteAllProgressForGoal(resolvedLocalGoalId)
                remoteProgress.forEach { p ->
                    goalProgressDao.insert(
                        GoalProgress(
                            id = goalProgressDao.getByClientId(p.clientId)?.id ?: 0,
                            clientId = p.clientId,
                            goalId = resolvedLocalGoalId,
                            date = parseIsoToMillisOrNow(p.date),
                            projectedRaceTime = p.projectedRaceTime,
                            sessionId = p.sessionId
                        )
                    )
                }
            }
        }
    }

    suspend fun pushGoal(goal: Goal) {
        withContext(Dispatchers.IO) {
            if (goal.clientId.isBlank()) {
                throw IllegalArgumentException("Goal.clientId is blank; generate a UUID before pushing")
            }

            val payload = buildJsonObject {
                put("client_id", goal.clientId)
                put("swimmer_id", goal.swimmerId)
                put("team_id", goal.teamId)
                put("event_name", goal.eventName)
                put("goal_time", goal.goalTime)
                put("start_date", millisToIso(goal.startDate))
                put("end_date", millisToIso(goal.endDate))
                put("goal_type", goal.goalType.name)
                put("is_active", goal.isActive)
            }

            try {
                supabase.from("goals").insert(payload)
            } catch (e: Exception) {
                if (e.message?.contains("duplicate", ignoreCase = true) == true ||
                    e.message?.contains("unique", ignoreCase = true) == true
                ) {
                    supabase.from("goals").update(payload) {
                        filter { eq("client_id", goal.clientId) }
                    }
                } else {
                    throw e
                }
            }
        }
    }

    suspend fun pushGoalProgress(progress: GoalProgress) {
        withContext(Dispatchers.IO) {
            if (progress.clientId.isBlank()) {
                throw IllegalArgumentException("GoalProgress.clientId is blank; generate a UUID before pushing")
            }

            val localGoal = goalDao.getById(progress.goalId)
                ?: throw IllegalStateException("No local goal found for goalId=${progress.goalId}")

            val remoteGoalId = resolveRemoteGoalIdByClientId(localGoal.clientId)
                ?: throw IllegalStateException("Could not resolve remote goal id for goal clientId=${localGoal.clientId}")

            val payload = buildJsonObject {
                put("client_id", progress.clientId)
                put("goal_id", remoteGoalId)
                put("date", millisToIso(progress.date))
                put("projected_race_time", progress.projectedRaceTime)
                progress.sessionId?.let { put("session_id", it) }
            }

            try {
                supabase.from("goal_progress").insert(payload)
            } catch (e: Exception) {
                if (e.message?.contains("duplicate", ignoreCase = true) == true ||
                    e.message?.contains("unique", ignoreCase = true) == true
                ) {
                    supabase.from("goal_progress").update(payload) {
                        filter { eq("client_id", progress.clientId) }
                    }
                } else {
                    throw e
                }
            }
        }
    }

    suspend fun deleteGoalByClientId(goalClientId: String) {
        withContext(Dispatchers.IO) {
            if (goalClientId.isBlank()) return@withContext
            supabase.from("goals").delete {
                filter { eq("client_id", goalClientId) }
            }
        }
    }

    private suspend fun resolveRemoteGoalIdByClientId(goalClientId: String): Int? {
        if (goalClientId.isBlank()) return null

        val jsonStr = supabase.from("goals").select {
            filter { eq("client_id", goalClientId) }
            limit(1)
        }.data

        @Serializable
        data class RemoteGoalIdOnly(val id: Int)

        return runCatching {
            json.decodeFromString<List<RemoteGoalIdOnly>>(jsonStr).firstOrNull()?.id
        }.getOrNull()
    }

    private fun parseIsoToMillisOrNow(value: String): Long {
        return value.toLongOrNull()
            ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
            ?: System.currentTimeMillis()
    }

    private fun millisToIso(millis: Long): String {
        return runCatching { Instant.ofEpochMilli(millis).toString() }
            .getOrDefault(Instant.now().toString())
    }
}
