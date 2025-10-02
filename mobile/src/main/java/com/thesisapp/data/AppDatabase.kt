package com.thesisapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Swimmer::class, SwimData::class, MlResult::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun swimmerDao(): SwimmerDao
    abstract fun swimDataDao(): SwimDataDao
    abstract fun mlResultDao(): MlResultDao

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