package com.thesisapp.utils

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Coach
import com.thesisapp.data.non_dao.User
import com.thesisapp.data.non_dao.UserRole as DbUserRole
import java.util.UUID

object LocalUserBootstrapper {
    private const val PREF = "local_user_bootstrapper"

    @VisibleForTesting
    internal const val KEY_USER_ID_PREFIX = "user_id."

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getOrCreateStableUserIdForEmail(context: Context, email: String): String {
        val normalized = email.trim().lowercase()
        val key = KEY_USER_ID_PREFIX + normalized
        val p = prefs(context)
        val existing = p.getString(key, null)
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        p.edit().putString(key, newId).apply()
        return newId
    }

    suspend fun ensureRoomUserForAuth(context: Context, db: AppDatabase): String? {
        val authUser = AuthManager.currentUser(context) ?: return null
        val userId = getOrCreateStableUserIdForEmail(context, authUser.email)
        val dbRole = if (authUser.role == UserRole.COACH) DbUserRole.COACH else DbUserRole.SWIMMER
        db.userDao().insertUser(User(id = userId, email = authUser.email, role = dbRole))
        return userId
    }

    suspend fun ensureRoomCoachForAuth(context: Context, db: AppDatabase, teamId: Int? = null) {
        val authUser = AuthManager.currentUser(context) ?: return
        if (authUser.role != UserRole.COACH) return

        val userId = ensureRoomUserForAuth(context, db) ?: return
        val resolvedTeamId = teamId ?: AuthManager.currentTeamId(context)
        db.coachDao().insertCoach(
            Coach(
                userId = userId,
                name = authUser.email,
                teamId = resolvedTeamId
            )
        )
    }

    suspend fun linkExistingSwimmerToAuthUserIfPossible(context: Context, db: AppDatabase) {
        val authUser = AuthManager.currentUser(context) ?: return
        if (authUser.role != UserRole.SWIMMER) return

        val userId = ensureRoomUserForAuth(context, db) ?: return
        val teamId = AuthManager.currentTeamId(context)
        val swimmerId = AuthManager.getLinkedSwimmerId(context, authUser.email, teamId)
        if (swimmerId != null) {
            db.swimmerDao().setUserIdForSwimmer(swimmerId = swimmerId, userId = userId)
        }
    }

    suspend fun createStandaloneSwimmerUser(db: AppDatabase): String {
        val userId = UUID.randomUUID().toString()
        db.userDao().insertUser(User(id = userId, email = "$userId@local", role = DbUserRole.SWIMMER))
        return userId
    }
}
