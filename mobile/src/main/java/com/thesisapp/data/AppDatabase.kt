package com.thesisapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.thesisapp.data.dao.ExerciseDao
import com.thesisapp.data.dao.GoalDao
import com.thesisapp.data.dao.GoalProgressDao
import com.thesisapp.data.dao.MlResultDao
import com.thesisapp.data.dao.SwimDataDao
import com.thesisapp.data.dao.SwimmerDao
import com.thesisapp.data.dao.TeamDao
import com.thesisapp.data.dao.TeamInvitationDao
import com.thesisapp.data.dao.TeamMembershipDao
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.Goal
import com.thesisapp.data.non_dao.GoalProgress
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.data.non_dao.SwimData
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.data.non_dao.Team
import com.thesisapp.data.non_dao.TeamInvitation
import com.thesisapp.data.non_dao.TeamMembership

@Database(
    entities = [Team::class, Swimmer::class, TeamMembership::class, Exercise::class, SwimData::class, MlResult::class, TeamInvitation::class, Goal::class, GoalProgress::class],
    version = 22,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun swimmerDao(): SwimmerDao
    abstract fun swimDataDao(): SwimDataDao
    abstract fun mlResultDao(): MlResultDao
    abstract fun teamDao(): TeamDao
    abstract fun teamMembershipDao(): TeamMembershipDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun teamInvitationDao(): TeamInvitationDao
    abstract fun goalDao(): GoalDao
    abstract fun goalProgressDao(): GoalProgressDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "swimmer_db"
                )
                    .fallbackToDestructiveMigration() // Wipes DB if schema changed (safe for dev)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
