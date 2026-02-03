package com.thesisapp.utils

import android.content.Context
import android.content.SharedPreferences

enum class UserRole { COACH, SWIMMER }

data class AuthUser(
    val email: String,
    val role: UserRole
)

/**
 * Extremely simple fake auth + membership store backed by SharedPreferences.
 * Persisted keys:
 * - current_email, current_role, current_team_id
 * - users.<email>.password
 * - users.<email>.role
 * - coach_teams.<email> -> comma-separated team ids
 * - swimmer_links.<email>.<teamId> -> swimmerId
 */
object AuthManager {
    private const val PREF = "auth_prefs"

    private const val KEY_CURRENT_EMAIL = "current_email"
    private const val KEY_CURRENT_ROLE = "current_role"
    private const val KEY_CURRENT_TEAM_ID = "current_team_id"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isLoggedIn(context: Context): Boolean =
        prefs(context).contains(KEY_CURRENT_EMAIL) && prefs(context).contains(KEY_CURRENT_ROLE)

    fun currentUser(context: Context): AuthUser? {
        val p = prefs(context)
        val email = p.getString(KEY_CURRENT_EMAIL, null) ?: return null
        val roleStr = p.getString(KEY_CURRENT_ROLE, null) ?: return null
        val role = runCatching { UserRole.valueOf(roleStr) }.getOrNull() ?: return null
        return AuthUser(email, role)
    }

    fun setCurrentUser(context: Context, email: String, role: UserRole) {
        prefs(context).edit()
            .putString(KEY_CURRENT_EMAIL, email)
            .putString(KEY_CURRENT_ROLE, role.name)
            .apply()
    }

    fun currentTeamId(context: Context): Int? =
        if (!prefs(context).contains(KEY_CURRENT_TEAM_ID)) null else prefs(context).getInt(KEY_CURRENT_TEAM_ID, -1).takeIf { it > 0 }

    fun setCurrentTeamId(context: Context, teamId: Int?) {
        prefs(context).edit().apply {
            if (teamId == null) remove(KEY_CURRENT_TEAM_ID) else putInt(KEY_CURRENT_TEAM_ID, teamId)
        }.apply()
    }

    fun register(context: Context, email: String, password: String, role: UserRole): Boolean {
        val p = prefs(context)
        if (p.contains("users.$email.password")) return false
        p.edit()
            .putString("users.$email.password", password)
            .putString("users.$email.role", role.name)
            .apply()
        return true
    }

    fun login(context: Context, email: String, password: String, role: UserRole): Boolean {
        val p = prefs(context)
        val storedPw = p.getString("users.$email.password", null) ?: return false
        val storedRole = p.getString("users.$email.role", null) ?: return false
        if (storedPw != password) return false
        if (storedRole != role.name) return false
        p.edit()
            .putString(KEY_CURRENT_EMAIL, email)
            .putString(KEY_CURRENT_ROLE, role.name)
            .apply()
        return true
    }

    fun logout(context: Context) {
        prefs(context).edit()
            .remove(KEY_CURRENT_EMAIL)
            .remove(KEY_CURRENT_ROLE)
            .remove(KEY_CURRENT_TEAM_ID)
            .apply()
    }

    // Coach teams management
    fun addCoachTeam(context: Context, email: String, teamId: Int) {
        val p = prefs(context)
        val key = "coach_teams.$email"
        val existing = p.getString(key, "")!!.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }.toMutableSet()
        existing.add(teamId)
        p.edit().putString(key, existing.joinToString(",")).apply()
    }

    fun getCoachTeams(context: Context, email: String): List<Int> {
        val p = prefs(context)
        val key = "coach_teams.$email"
        return p.getString(key, "")!!.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
    }

    // Swimmer teams management
    fun addSwimmerTeam(context: Context, email: String, teamId: Int) {
        val p = prefs(context)
        val key = "swimmer_teams.$email"
        val existing = p.getString(key, "")!!.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }.toMutableSet()
        existing.add(teamId)
        p.edit().putString(key, existing.joinToString(",")).apply()
    }

    fun getSwimmerTeams(context: Context, email: String): List<Int> {
        val p = prefs(context)
        val key = "swimmer_teams.$email"
        return p.getString(key, "")!!.split(',').filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() }
    }

    // Swimmer links: map teamId -> swimmerId for a given swimmer account (email)
    fun linkSwimmerToTeam(context: Context, email: String, teamId: Int, swimmerId: Int) {
        prefs(context).edit().putInt("swimmer_links.$email.$teamId", swimmerId).apply()
        addSwimmerTeam(context, email, teamId)
    }

    fun getLinkedSwimmerId(context: Context, email: String, teamId: Int?): Int? {
        if (teamId == null) return null
        val key = "swimmer_links.$email.$teamId"
        return if (prefs(context).contains(key)) prefs(context).getInt(key, -1).takeIf { it > 0 } else null
    }

    // Team-coach membership management
    private fun teamCoachesKey(teamId: Int) = "team_coaches.$teamId"

    fun addCoachToTeam(context: Context, teamId: Int, coachEmail: String) {
        val p = prefs(context)
        val key = teamCoachesKey(teamId)
        val set = p.getString(key, "")!!.split(',').filter { it.isNotBlank() }.toMutableSet()
        set.add(coachEmail)
        p.edit().putString(key, set.joinToString(",")).apply()
    }

    fun removeCoachFromTeam(context: Context, teamId: Int, coachEmail: String) {
        val p = prefs(context)
        val key = teamCoachesKey(teamId)
        val set = p.getString(key, "")!!.split(',').filter { it.isNotBlank() }.toMutableSet()
        set.remove(coachEmail)
        p.edit().putString(key, set.joinToString(",")).apply()
    }

    fun getTeamCoaches(context: Context, teamId: Int): List<String> {
        val p = prefs(context)
        val key = teamCoachesKey(teamId)
        return p.getString(key, "")!!.split(',').filter { it.isNotBlank() }
    }

    fun userExists(context: Context, email: String): Boolean {
        return prefs(context).contains("users.$email.password")
    }

    fun getUserRole(context: Context, email: String): UserRole? {
        val role = prefs(context).getString("users.$email.role", null) ?: return null
        return runCatching { UserRole.valueOf(role) }.getOrNull()
    }
}
