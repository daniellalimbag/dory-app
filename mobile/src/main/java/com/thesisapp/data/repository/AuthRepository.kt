package com.thesisapp.data.repository

import android.content.Context
import android.util.Log
import com.thesisapp.data.dao.TeamDao
import com.thesisapp.data.dao.TeamMembershipDao
import com.thesisapp.data.dao.SwimmerDao
import com.thesisapp.data.dao.UserDao
import com.thesisapp.data.non_dao.Team
import com.thesisapp.data.non_dao.TeamMembership
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.LocalUserBootstrapper
import com.thesisapp.utils.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val bootstrapper: LocalUserBootstrapper,
    private val userDao: UserDao,
    private val teamDao: TeamDao,
    private val swimmerDao: SwimmerDao,
    private val teamMembershipDao: TeamMembershipDao,
    @ApplicationContext private val context: Context
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RemoteUserRow(
        val id: String,
        val email: String,
        val role: String
    )

    @Serializable
    private data class RemoteCoachRow(
        @SerialName("user_id") val userId: String,
        val name: String,
        @SerialName("team_id") val teamId: Int? = null
    )

    @Serializable
    private data class RemoteSwimmerRow(
        val id: Long,
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

    @Serializable
    private data class RemoteTeamMembershipRow(
        val id: Long,
        @SerialName("team_id") val teamId: Int,
        @SerialName("user_id") val userId: String,
        val role: String
    )

    @Serializable
    private data class RemoteTeamRow(
        val id: Int,
        val name: String,
        @SerialName("join_code") val joinCode: String,
        @SerialName("logo_path") val logoPath: String? = null
    )

    suspend fun signUp(
        email: String,
        password: String,
        role: UserRole,
        name: String
    ) {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }

        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("Supabase signUp did not create an authenticated user")

        val roleStr = role.name
        val usersPayload = buildJsonObject {
            put("id", userId)
            put("email", email)
            put("role", roleStr)
        }
        supabase.from("users").insert(usersPayload)

        when (role) {
            UserRole.COACH -> {
                val coachPayload = buildJsonObject {
                    put("user_id", userId)
                    put("name", name)
                }
                supabase.from("coaches").insert(coachPayload)
            }

            UserRole.SWIMMER -> {
                val swimmerPayload = buildJsonObject {
                    put("user_id", userId)
                    put("name", name)
                    put("birthday", "1970-01-01")
                    put("height", 0)
                    put("weight", 0)
                    put("sex", "")
                    put("wingspan", 0)
                    put("category", "SPRINT")
                    put("specialty", JsonNull)
                }
                supabase.from("swimmers").insert(swimmerPayload)
            }
        }

        withContext(Dispatchers.IO) {
            bootstrapper.ensureLocalDataForSupabaseUser(
                userId = userId,
                email = email,
                role = role,
                name = name
            )
        }

        AuthManager.logout(context)
        AuthManager.setCurrentUser(context, email = email, role = role)
    }

    suspend fun signIn(
        email: String,
        password: String
    ) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val userId = supabase.auth.currentUserOrNull()?.id
            ?: error("Supabase signIn did not create an authenticated user")

        val userRowJson = supabase.from("users").select {
            filter {
                eq("id", userId)
            }
            limit(1)
        }.data

        val remoteUser = json.decodeFromString<List<RemoteUserRow>>(userRowJson).firstOrNull()
            ?: error("No user profile found in users table")

        val role = runCatching { UserRole.valueOf(remoteUser.role) }.getOrElse {
            error("Invalid role in users table: ${remoteUser.role}")
        }

        val resolvedName = when (role) {
            UserRole.COACH -> {
                val coachJson = supabase.from("coaches").select {
                    filter { eq("user_id", userId) }
                    limit(1)
                }.data
                json.decodeFromString<List<RemoteCoachRow>>(coachJson).firstOrNull()?.name ?: email
            }

            UserRole.SWIMMER -> {
                val swimmerJson = supabase.from("swimmers").select {
                    filter { eq("user_id", userId) }
                    limit(1)
                }.data
                json.decodeFromString<List<RemoteSwimmerRow>>(swimmerJson).firstOrNull()?.name ?: email
            }
        }

        val coachTeamIds: List<Int> = if (role == UserRole.COACH) {
            val membershipsJson = supabase.from("team_memberships").select {
                filter {
                    eq("user_id", userId)
                    eq("role", "coach")
                }
            }.data

            runCatching {
                json.decodeFromString<List<RemoteTeamMembershipRow>>(membershipsJson)
            }.getOrElse {
                Log.d("DEBUG", "Failed to decode team_memberships (coach)", it)
                emptyList()
            }.map { it.teamId }.distinct()
        } else {
            emptyList()
        }

        val coachTeamId: Int? = coachTeamIds.firstOrNull()

        if (role == UserRole.COACH && coachTeamIds.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                coachTeamIds.forEach { teamId ->
                    val teamJson = supabase.from("teams").select {
                        filter { eq("id", teamId) }
                        limit(1)
                    }.data

                    val remoteTeam = json.decodeFromString<List<RemoteTeamRow>>(teamJson).firstOrNull()
                    if (remoteTeam != null) {
                        teamDao.insert(
                            Team(
                                id = remoteTeam.id,
                                name = remoteTeam.name,
                                joinCode = remoteTeam.joinCode,
                                logoPath = remoteTeam.logoPath
                            )
                        )
                    }
                }
            }
        }

        withContext(Dispatchers.IO) {
            bootstrapper.ensureLocalDataForSupabaseUser(
                userId = userId,
                email = remoteUser.email,
                role = role,
                name = resolvedName,
                coachTeamId = coachTeamId
            )
        }

        AuthManager.logout(context)
        AuthManager.setCurrentUser(context, email = remoteUser.email, role = role)

        if (role == UserRole.COACH && coachTeamIds.isNotEmpty()) {
            if (coachTeamId != null) {
                AuthManager.setCurrentTeamId(context, coachTeamId)
            }
            coachTeamIds.forEach { teamId ->
                AuthManager.addCoachTeam(context, remoteUser.email, teamId)
                AuthManager.addCoachToTeam(context, teamId, remoteUser.email)
            }
        }

        if (role == UserRole.SWIMMER) {
            withContext(Dispatchers.IO) {
                val swimmerJson = supabase.from("swimmers").select {
                    filter { eq("user_id", userId) }
                    limit(1)
                }.data

                val remoteSwimmer = json.decodeFromString<List<RemoteSwimmerRow>>(swimmerJson).firstOrNull()
                    ?: return@withContext

                val swimmerId = remoteSwimmer.id.toInt()

                // Ensure the local swimmer row uses the SAME numeric id as Supabase.
                val localExisting = swimmerDao.getByUserId(userId)
                val localRow = if (localExisting == null) {
                    com.thesisapp.data.non_dao.Swimmer(
                        id = swimmerId,
                        userId = userId,
                        name = remoteSwimmer.name,
                        birthday = remoteSwimmer.birthday,
                        height = remoteSwimmer.height,
                        weight = remoteSwimmer.weight,
                        sex = remoteSwimmer.sex,
                        wingspan = remoteSwimmer.wingspan,
                        category = runCatching { com.thesisapp.data.non_dao.ExerciseCategory.valueOf(remoteSwimmer.category) }
                            .getOrElse { com.thesisapp.data.non_dao.ExerciseCategory.SPRINT },
                        specialty = remoteSwimmer.specialty
                    )
                } else {
                    localExisting.copy(
                        id = swimmerId,
                        userId = userId,
                        name = remoteSwimmer.name,
                        birthday = remoteSwimmer.birthday,
                        height = remoteSwimmer.height,
                        weight = remoteSwimmer.weight,
                        sex = remoteSwimmer.sex,
                        wingspan = remoteSwimmer.wingspan,
                        category = runCatching { com.thesisapp.data.non_dao.ExerciseCategory.valueOf(remoteSwimmer.category) }
                            .getOrElse { com.thesisapp.data.non_dao.ExerciseCategory.SPRINT },
                        specialty = remoteSwimmer.specialty
                    )
                }

                swimmerDao.insertSwimmer(localRow)

                val membershipsJson = supabase.from("team_memberships").select {
                    filter {
                        eq("user_id", userId)
                        eq("role", "swimmer")
                    }
                }.data

                val memberships = runCatching {
                    json.decodeFromString<List<RemoteTeamMembershipRow>>(membershipsJson)
                }.getOrElse {
                    Log.d("DEBUG", "Failed to decode team_memberships (swimmer)", it)
                    emptyList()
                }

                if (memberships.isNotEmpty()) {
                    val teamIds = memberships.map { it.teamId }.distinct()

                    teamIds.forEach { teamId ->
                        AuthManager.linkSwimmerToTeam(context, remoteUser.email, teamId, swimmerId)

                        val teamJson = supabase.from("teams").select {
                            filter { eq("id", teamId) }
                            limit(1)
                        }.data
                        val remoteTeam = json.decodeFromString<List<RemoteTeamRow>>(teamJson).firstOrNull()
                        if (remoteTeam != null) {
                            teamDao.insert(
                                Team(
                                    id = remoteTeam.id,
                                    name = remoteTeam.name,
                                    joinCode = remoteTeam.joinCode,
                                    logoPath = remoteTeam.logoPath
                                )
                            )
                        }

                        val existingMembership = teamMembershipDao.getMembership(teamId = teamId, swimmerId = swimmerId)
                        if (existingMembership == null) {
                            teamMembershipDao.insert(TeamMembership(teamId = teamId, swimmerId = swimmerId))
                        }
                    }

                    if (AuthManager.currentTeamId(context) == null) {
                        AuthManager.setCurrentTeamId(context, teamIds.first())
                    }
                }
            }
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
        AuthManager.logout(context)
    }
}
