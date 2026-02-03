package com.thesisapp.data.repository

import com.thesisapp.data.dao.SwimmerDao
import com.thesisapp.data.dao.TeamMembershipDao
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.data.non_dao.TeamMembership
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
class TeamSyncRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val swimmerDao: SwimmerDao,
    private val membershipDao: TeamMembershipDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RemoteMembershipRow(
        val id: Int,
        @SerialName("team_id") val teamId: Int,
        @SerialName("swimmer_id") val swimmerId: Int,
        @SerialName("joined_at") val joinedAt: Long? = null
    )

    @Serializable
    private data class RemoteSwimmerRow(
        val id: Int,
        @SerialName("user_id") val userId: String,
        val name: String,
        val birthday: String,
        val height: Float,
        val weight: Float,
        val sex: String,
        val wingspan: Float,
        val category: String,
        val specialty: String? = null
    )

    suspend fun syncTeamMembers(teamId: Int) {
        withContext(Dispatchers.IO) {
            val membershipsJson = supabase.from("team_memberships").select {
                filter { eq("team_id", teamId) }
            }.data

            val remoteMemberships = json.decodeFromString<List<RemoteMembershipRow>>(membershipsJson)

            val swimmerIds = remoteMemberships.map { it.swimmerId }.distinct()

            val remoteSwimmers: List<RemoteSwimmerRow> = if (swimmerIds.isEmpty()) {
                emptyList()
            } else {
                val swimmers = mutableListOf<RemoteSwimmerRow>()
                // Avoid relying on PostgREST join syntax: fetch swimmers in small chunks.
                // Typical team sizes are small; this remains fine.
                swimmerIds.chunked(50).forEach { chunk ->
                    chunk.forEach { id ->
                        val swimmerJson = supabase.from("swimmers").select {
                            filter { eq("id", id) }
                            limit(1)
                        }.data
                        val row = json.decodeFromString<List<RemoteSwimmerRow>>(swimmerJson).firstOrNull()
                        if (row != null) swimmers.add(row)
                    }
                }
                swimmers
            }

            // Upsert swimmers (preserve local schema; map category string to ExerciseCategory name)
            remoteSwimmers.forEach { rs ->
                val existing = swimmerDao.getById(rs.id)
                val categoryEnum = runCatching { com.thesisapp.data.non_dao.ExerciseCategory.valueOf(rs.category) }
                    .getOrElse { com.thesisapp.data.non_dao.ExerciseCategory.SPRINT }

                val mapped = if (existing != null) {
                    existing.copy(
                        userId = rs.userId,
                        name = rs.name,
                        birthday = rs.birthday,
                        height = rs.height,
                        weight = rs.weight,
                        sex = rs.sex,
                        wingspan = rs.wingspan,
                        category = categoryEnum,
                        specialty = rs.specialty
                    )
                } else {
                    Swimmer(
                        id = rs.id,
                        userId = rs.userId,
                        name = rs.name,
                        birthday = rs.birthday,
                        height = rs.height,
                        weight = rs.weight,
                        sex = rs.sex,
                        wingspan = rs.wingspan,
                        category = categoryEnum,
                        specialty = rs.specialty
                    )
                }

                swimmerDao.insertSwimmer(mapped)
            }

            // Upsert memberships
            val mappedMemberships = remoteMemberships.map { rm ->
                TeamMembership(
                    id = rm.id,
                    teamId = rm.teamId,
                    swimmerId = rm.swimmerId,
                    joinedAt = rm.joinedAt ?: System.currentTimeMillis()
                )
            }
            membershipDao.upsertAll(mappedMemberships)

            // Optional reconciliation: delete local memberships that no longer exist remotely
            val localCount = membershipDao.getSwimmerCountForTeam(teamId)
            if (swimmerIds.isNotEmpty() || localCount == 0) {
                membershipDao.removeMembershipsNotInTeam(teamId, swimmerIds)
            }
        }
    }
}
