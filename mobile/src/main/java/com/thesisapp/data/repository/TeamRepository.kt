package com.thesisapp.data.repository

import com.thesisapp.data.dao.TeamDao
import com.thesisapp.data.non_dao.Team
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TeamRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val teamDao: TeamDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Serializable
    private data class RemoteTeamRow(
        val id: Int,
        val name: String,
        @SerialName("join_code") val joinCode: String,
        @SerialName("logo_path") val logoPath: String? = null
    )

    @Serializable
    private data class RemoteMembershipRow(
        val id: Int,
        @SerialName("team_id") val teamId: Int,
        @SerialName("swimmer_id") val swimmerId: Int
    )

    suspend fun joinTeam(teamId: Int, swimmerId: Int) {
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("team_id", teamId)
                put("swimmer_id", swimmerId)
            }

            // Throws on RLS/network errors.
            supabase.from("team_memberships").insert(payload)

            // Verify it actually exists (helps debug and avoids false-success).
            val verifyJson = supabase.from("team_memberships").select {
                filter {
                    eq("team_id", teamId)
                    eq("swimmer_id", swimmerId)
                }
                limit(1)
            }.data

            val exists = runCatching {
                json.decodeFromString<List<RemoteMembershipRow>>(verifyJson).isNotEmpty()
            }.getOrDefault(false)

            if (!exists) {
                error("Supabase insert completed but membership row not found (teamId=$teamId, swimmerId=$swimmerId)")
            }
        }
    }

    suspend fun createTeam(
        name: String,
        coachId: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val joinCode = generateUniqueJoinCode(length = 6)

                val insertPayload = buildJsonObject {
                    put("name", name)
                    put("join_code", joinCode)
                }

                supabase.from("teams").insert(insertPayload)

                val teamJson = supabase.from("teams").select {
                    filter {
                        eq("join_code", joinCode)
                    }
                    limit(1)
                }.data

                val remoteTeam = json.decodeFromString<List<RemoteTeamRow>>(teamJson).firstOrNull()
                    ?: error("Team was inserted but could not be fetched (join_code=$joinCode)")

                val updateCoachPayload = buildJsonObject {
                    put("team_id", remoteTeam.id)
                }

                supabase.from("coaches").update(updateCoachPayload) {
                    filter {
                        eq("user_id", coachId)
                    }
                }

                teamDao.insert(
                    Team(
                        id = remoteTeam.id,
                        name = remoteTeam.name,
                        joinCode = remoteTeam.joinCode,
                        logoPath = remoteTeam.logoPath
                    )
                )

                remoteTeam.id.toString()
            }
        }
    }

    suspend fun uploadTeamLogo(teamId: Long, byteArray: ByteArray): String {
        return withContext(Dispatchers.IO) {
            val objectPath = "logos/$teamId/logo.png"

            supabase.storage.from("team-logos").upload(
                path = objectPath,
                data = byteArray
            ) {
                upsert = true
                contentType = ContentType.Image.PNG
            }

            val updatePayload = buildJsonObject {
                put("logo_path", objectPath)
            }

            supabase.from("teams").update(updatePayload) {
                filter {
                    eq("id", teamId)
                }
            }

            val verifyJson = supabase.from("teams").select {
                filter {
                    eq("id", teamId)
                }
                limit(1)
            }.data

            val updated = verifyJson.contains(objectPath)
            if (!updated) {
                error("Supabase update completed but teams.logo_path was not updated (teamId=$teamId)")
            }

            objectPath
        }
    }

    suspend fun updateTeamName(teamId: Long, newName: String) {
        withContext(Dispatchers.IO) {
            val updatePayload = buildJsonObject {
                put("name", newName)
            }

            supabase.from("teams").update(updatePayload) {
                filter {
                    eq("id", teamId)
                }
            }

            val verifyJson = supabase.from("teams").select {
                filter { eq("id", teamId) }
                limit(1)
            }.data

            if (!verifyJson.contains(newName)) {
                error("Supabase update completed but teams.name was not updated (teamId=$teamId)")
            }

            val local = teamDao.getById(teamId.toInt())
            if (local != null) {
                teamDao.update(local.copy(name = newName))
            }
        }
    }

    suspend fun getTeamLogoSignedUrl(logoPath: String): String {
        return withContext(Dispatchers.IO) {
            supabase.storage
                .from("team-logos")
                .createSignedUrl(path = logoPath, expiresIn = 10.minutes)
        }
    }

    private suspend fun generateUniqueJoinCode(length: Int, maxAttempts: Int = 10): String {
        repeat(maxAttempts) {
            val candidate = generateJoinCode(length)
            val existingJson = supabase.from("teams").select {
                filter {
                    eq("join_code", candidate)
                }
                limit(1)
            }.data

            val exists = runCatching {
                json.decodeFromString<List<RemoteTeamRow>>(existingJson).isNotEmpty()
            }.getOrDefault(false)

            if (!exists) return candidate
        }

        error("Unable to generate a unique join code after $maxAttempts attempts")
    }

    private fun generateJoinCode(length: Int): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString(length) {
            repeat(length) {
                append(alphabet.random())
            }
        }
    }
}
