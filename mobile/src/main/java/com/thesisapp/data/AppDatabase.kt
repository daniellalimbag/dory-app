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
import com.thesisapp.data.dao.PersonalBestDao
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
import com.thesisapp.data.non_dao.PersonalBest
import com.thesisapp.data.non_dao.SwimData
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.data.non_dao.Team
import com.thesisapp.data.non_dao.TeamInvitation
import com.thesisapp.data.non_dao.TeamMembership
import com.thesisapp.data.non_dao.User

@Database(
    entities = [Team::class, User::class, Coach::class, Swimmer::class, TeamMembership::class, Exercise::class, SwimData::class, MlResult::class, TeamInvitation::class, Goal::class, GoalProgress::class, PersonalBest::class],
    version = 27,
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
    abstract fun personalBestDao(): PersonalBestDao

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

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add primaryStroke column to swimmers table
                db.execSQL("ALTER TABLE `swimmers` ADD COLUMN `primaryStroke` TEXT DEFAULT 'FREESTYLE'")
                
                // Add strokeType and targetTime columns to exercises table
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `strokeType` TEXT")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `targetTime` REAL")
                
                // Create personal_bests table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `personal_bests` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`swimmerId` INTEGER NOT NULL, " +
                        "`distance` INTEGER NOT NULL, " +
                        "`strokeType` TEXT NOT NULL, " +
                        "`bestTime` REAL NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`swimmerId`) REFERENCES `swimmers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                    ")"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_personal_bests_swimmerId_distance_strokeType` ON `personal_bests` (`swimmerId`, `distance`, `strokeType`)")
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add clientId columns for Supabase sync (client_id UUID)
                // Must be NOT NULL to match Kotlin non-null fields in Room entities
                db.execSQL("ALTER TABLE `goals` ADD COLUMN `clientId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `goal_progress` ADD COLUMN `clientId` TEXT NOT NULL DEFAULT ''")

                // Backfill unique-ish IDs for existing rows (UUID-like string)
                db.execSQL(
                    "UPDATE `goals` SET `clientId` = " +
                        "lower(hex(randomblob(4))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(6))) " +
                        "WHERE `clientId` IS NULL OR `clientId` = ''"
                )
                db.execSQL(
                    "UPDATE `goal_progress` SET `clientId` = " +
                        "lower(hex(randomblob(4))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(6))) " +
                        "WHERE `clientId` IS NULL OR `clientId` = ''"
                )

                // Add indices to support fast lookups
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goals_clientId` ON `goals` (`clientId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goal_progress_clientId` ON `goal_progress` (`clientId`)")
            }
        }

        private val MIGRATION_25_27 = object : Migration(25, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add clientId columns for Supabase sync (client_id UUID)
                // Directly to v27 with correct NOT NULL constraint
                db.execSQL("ALTER TABLE `goals` ADD COLUMN `clientId` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `goal_progress` ADD COLUMN `clientId` TEXT NOT NULL DEFAULT ''")

                // Backfill unique-ish IDs for existing rows (UUID-like string)
                db.execSQL(
                    "UPDATE `goals` SET `clientId` = " +
                        "lower(hex(randomblob(4))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(6))) " +
                        "WHERE `clientId` = ''"
                )
                db.execSQL(
                    "UPDATE `goal_progress` SET `clientId` = " +
                        "lower(hex(randomblob(4))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(2))) || '-' || " +
                        "lower(hex(randomblob(6))) " +
                        "WHERE `clientId` = ''"
                )

                // Add indices to support fast lookups
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goals_clientId` ON `goals` (`clientId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_goal_progress_clientId` ON `goal_progress` (`clientId`)")
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
                    .addMigrations(MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
