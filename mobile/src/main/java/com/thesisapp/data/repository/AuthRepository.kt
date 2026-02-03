package com.thesisapp.data.repository

import android.content.Context
import com.thesisapp.data.dao.UserDao
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
                    put("team_id", JsonNull)
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

        val coachTeamId: Int? = if (role == UserRole.COACH) {
            val coachJson = supabase.from("coaches").select {
                filter { eq("user_id", userId) }
                limit(1)
            }.data
            json.decodeFromString<List<RemoteCoachRow>>(coachJson).firstOrNull()?.teamId
        } else {
            null
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

        if (role == UserRole.COACH && coachTeamId != null) {
            AuthManager.setCurrentTeamId(context, coachTeamId)
            AuthManager.addCoachTeam(context, remoteUser.email, coachTeamId)
            AuthManager.addCoachToTeam(context, coachTeamId, remoteUser.email)
        }
    }

    suspend fun signOut() {
        supabase.auth.signOut()
        AuthManager.logout(context)
    }
}
