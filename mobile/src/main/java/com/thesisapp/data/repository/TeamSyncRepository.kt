package com.thesisapp.data.repository

import com.thesisapp.data.dao.SwimmerDao
import com.thesisapp.data.dao.TeamMembershipDao
import com.thesisapp.data.dao.UserDao
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.data.non_dao.TeamMembership
import com.thesisapp.data.non_dao.User
import com.thesisapp.data.non_dao.UserRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    private val membershipDao: TeamMembershipDao,
    private val userDao: UserDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RemoteMembershipRow(
        val id: Int,
        @SerialName("team_id") val teamId: Int,
        @SerialName("user_id") val userId: String,
        val role: String
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
                .filter { it.role.equals("swimmer", ignoreCase = true) }

            val swimmerUserIds = remoteMemberships.map { it.userId }.distinct()

            val remoteSwimmers: List<RemoteSwimmerRow> = if (swimmerUserIds.isEmpty()) {
                emptyList()
            } else {
                coroutineScope {
                    swimmerUserIds.chunked(50).flatMap { chunk ->
                        chunk.map { userId ->
                            async {
                                val swimmerJson = supabase.from("swimmers").select {
                                    filter { eq("user_id", userId) }
                                    limit(1)
                                }.data
                                json.decodeFromString<List<RemoteSwimmerRow>>(swimmerJson).firstOrNull()
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
            }

            remoteSwimmers.forEach { rs ->
                userDao.insertUser(
                    User(
                        id = rs.userId,
                        email = "remote-${rs.userId}@supabase",
                        role = UserRole.SWIMMER
                    )
                )
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
            val swimmerIdByUserId = remoteSwimmers.associate { it.userId to it.id }

            val mappedMemberships = remoteMemberships.mapNotNull { rm ->
                val swimmerId = swimmerIdByUserId[rm.userId] ?: return@mapNotNull null
                TeamMembership(
                    id = rm.id,
                    teamId = rm.teamId,
                    swimmerId = swimmerId,
                    joinedAt = System.currentTimeMillis()
                )
            }
            membershipDao.upsertAll(mappedMemberships)

            // Optional reconciliation: delete local memberships that no longer exist remotely
            val localCount = membershipDao.getSwimmerCountForTeam(teamId)
            val swimmerIds = mappedMemberships.map { it.swimmerId }.distinct()
            if (swimmerIds.isNotEmpty() || localCount == 0) {
                membershipDao.removeMembershipsNotInTeam(teamId, swimmerIds)
            }
        }
    }
}
