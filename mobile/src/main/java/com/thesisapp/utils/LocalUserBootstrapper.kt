package com.thesisapp.utils

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Coach
import com.thesisapp.data.non_dao.ExerciseCategory
import com.thesisapp.data.non_dao.User
import com.thesisapp.data.non_dao.UserRole as DbUserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalUserBootstrapper @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val db: AppDatabase
) {

    suspend fun ensureLocalDataForSupabaseUser(
        userId: String,
        email: String,
        role: UserRole,
        name: String
    ) {
        withContext(Dispatchers.IO) {
            val existingUser = db.userDao().getById(userId)
            if (existingUser == null) {
                val dbRole = if (role == UserRole.COACH) DbUserRole.COACH else DbUserRole.SWIMMER
                db.userDao().insertUser(User(id = userId, email = email, role = dbRole))
            }

            when (role) {
                UserRole.COACH -> {
                    val existingCoach = db.coachDao().getByUserId(userId)
                    if (existingCoach == null) {
                        db.coachDao().insertCoach(
                            Coach(
                                userId = userId,
                                name = name,
                                teamId = null
                            )
                        )
                    }
                }

                UserRole.SWIMMER -> {
                    val existingSwimmer = db.swimmerDao().getByUserId(userId)
                    if (existingSwimmer == null) {
                        db.swimmerDao().insertSwimmer(
                            com.thesisapp.data.non_dao.Swimmer(
                                userId = userId,
                                name = name,
                                birthday = "1970-01-01",
                                height = 0f,
                                weight = 0f,
                                sex = "",
                                wingspan = 0f,
                                category = ExerciseCategory.SPRINT,
                                specialty = null
                            )
                        )
                    }
                }
            }
        }
    }

    companion object {
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
            return withContext(Dispatchers.IO) {
                val authUser = AuthManager.currentUser(context) ?: return@withContext null
                val userId = getOrCreateStableUserIdForEmail(context, authUser.email)
                val dbRole = if (authUser.role == UserRole.COACH) DbUserRole.COACH else DbUserRole.SWIMMER
                db.userDao().insertUser(User(id = userId, email = authUser.email, role = dbRole))
                userId
            }
        }

        suspend fun ensureRoomCoachForAuth(context: Context, db: AppDatabase, teamId: Int? = null) {
            withContext(Dispatchers.IO) {
                val authUser = AuthManager.currentUser(context) ?: return@withContext
                if (authUser.role != UserRole.COACH) return@withContext

                val userId = ensureRoomUserForAuth(context, db) ?: return@withContext
                val resolvedTeamId = teamId ?: AuthManager.currentTeamId(context)
                db.coachDao().insertCoach(
                    Coach(
                        userId = userId,
                        name = authUser.email,
                        teamId = resolvedTeamId
                    )
                )
            }
        }

        suspend fun linkExistingSwimmerToAuthUserIfPossible(context: Context, db: AppDatabase) {
            withContext(Dispatchers.IO) {
                val authUser = AuthManager.currentUser(context) ?: return@withContext
                if (authUser.role != UserRole.SWIMMER) return@withContext

                val userId = ensureRoomUserForAuth(context, db) ?: return@withContext
                val teamId = AuthManager.currentTeamId(context)
                val swimmerId = AuthManager.getLinkedSwimmerId(context, authUser.email, teamId)
                if (swimmerId != null) {
                    db.swimmerDao().setUserIdForSwimmer(swimmerId = swimmerId, userId = userId)
                }
            }
        }

        suspend fun createStandaloneSwimmerUser(db: AppDatabase): String {
            return withContext(Dispatchers.IO) {
                val userId = UUID.randomUUID().toString()
                db.userDao().insertUser(User(id = userId, email = "$userId@local", role = DbUserRole.SWIMMER))
                userId
            }
        }
    }
}

