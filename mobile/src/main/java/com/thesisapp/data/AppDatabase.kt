package com.thesisapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.thesisapp.data.dao.ExerciseDao
import com.thesisapp.data.dao.CoachDao
import com.thesisapp.data.dao.GoalDao
import com.thesisapp.data.dao.GoalProgressDao
import com.thesisapp.data.dao.MlResultDao
import com.thesisapp.data.dao.SwimDataDao
import com.thesisapp.data.dao.SwimmerDao
import com.thesisapp.data.dao.TeamDao
import com.thesisapp.data.dao.TeamInvitationDao
import com.thesisapp.data.dao.TeamMembershipDao
import com.thesisapp.data.dao.UserDao
import com.thesisapp.data.non_dao.Coach
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.Goal
import com.thesisapp.data.non_dao.GoalProgress
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.data.non_dao.SwimData
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.data.non_dao.Team
import com.thesisapp.data.non_dao.TeamInvitation
import com.thesisapp.data.non_dao.TeamMembership
import com.thesisapp.data.non_dao.User

@Database(
    entities = [Team::class, User::class, Coach::class, Swimmer::class, TeamMembership::class, Exercise::class, SwimData::class, MlResult::class, TeamInvitation::class, Goal::class, GoalProgress::class],
    version = 24,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
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
    abstract fun userDao(): UserDao
    abstract fun coachDao(): CoachDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `users` (" +
                        "`id` TEXT NOT NULL, " +
                        "`email` TEXT NOT NULL, " +
                        "`role` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`)" +
                    ")"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `coaches` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`teamId` INTEGER, " +
                        "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`teamId`) REFERENCES `teams`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
                    ")"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_coaches_userId` ON `coaches` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coaches_teamId` ON `coaches` (`teamId`)")

                db.execSQL(
                    "INSERT OR IGNORE INTO `users` (`id`, `email`, `role`, `createdAt`) " +
                        "SELECT " +
                            "'local-swimmer-' || `id`, " +
                            "'local-swimmer-' || `id` || '@local', " +
                            "'SWIMMER', " +
                            "CAST(strftime('%s','now') AS INTEGER) * 1000 " +
                        "FROM `swimmers`"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `swimmers_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`userId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`birthday` TEXT NOT NULL, " +
                        "`height` REAL NOT NULL, " +
                        "`weight` REAL NOT NULL, " +
                        "`sex` TEXT NOT NULL, " +
                        "`wingspan` REAL NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`specialty` TEXT, " +
                        "FOREIGN KEY(`userId`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                    ")"
                )

                db.execSQL(
                    "INSERT INTO `swimmers_new` (" +
                        "`id`, `userId`, `name`, `birthday`, `height`, `weight`, `sex`, `wingspan`, `category`, `specialty`" +
                    ") " +
                    "SELECT " +
                        "`id`, " +
                        "'local-swimmer-' || `id`, " +
                        "`name`, `birthday`, `height`, `weight`, `sex`, `wingspan`, `category`, `specialty` " +
                    "FROM `swimmers`"
                )

                db.execSQL("DROP TABLE `swimmers`")
                db.execSQL("ALTER TABLE `swimmers_new` RENAME TO `swimmers`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_swimmers_userId` ON `swimmers` (`userId`)")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // New column for Team.logoPath
                db.execSQL("ALTER TABLE `teams` ADD COLUMN `logoPath` TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "swimmer_db"
                )
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_22_23, MIGRATION_23_24)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
