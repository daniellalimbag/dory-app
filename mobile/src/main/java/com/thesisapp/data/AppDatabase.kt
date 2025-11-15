package com.thesisapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Team::class, Swimmer::class, TeamMembership::class, Exercise::class, SwimData::class, MlResult::class, TeamInvitation::class, Goal::class, GoalProgress::class],
    version = 21,
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