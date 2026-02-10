package com.thesisapp.di

import android.content.Context
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.dao.CoachDao
import com.thesisapp.data.dao.ExerciseDao
import com.thesisapp.data.dao.GoalDao
import com.thesisapp.data.dao.GoalProgressDao
import com.thesisapp.data.dao.MlResultDao
import com.thesisapp.data.dao.PersonalBestDao
import com.thesisapp.data.dao.SwimDataDao
import com.thesisapp.data.dao.SwimmerDao
import com.thesisapp.data.dao.TeamDao
import com.thesisapp.data.dao.TeamMembershipDao
import com.thesisapp.data.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideUserDao(db: AppDatabase): UserDao = db.userDao()

    @Provides
    fun provideCoachDao(db: AppDatabase): CoachDao = db.coachDao()

    @Provides
    fun provideSwimmerDao(db: AppDatabase): SwimmerDao = db.swimmerDao()

    @Provides
    fun provideSwimDataDao(db: AppDatabase): SwimDataDao = db.swimDataDao()

    @Provides
    fun provideMlResultDao(db: AppDatabase): MlResultDao = db.mlResultDao()

    @Provides
    fun provideTeamDao(db: AppDatabase): TeamDao = db.teamDao()

    @Provides
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideTeamMembershipDao(db: AppDatabase): TeamMembershipDao = db.teamMembershipDao()

    @Provides
    fun provideGoalDao(db: AppDatabase): GoalDao = db.goalDao()

    @Provides
    fun provideGoalProgressDao(db: AppDatabase): GoalProgressDao = db.goalProgressDao()

    @Provides
    fun providePersonalBestDao(db: AppDatabase): PersonalBestDao = db.personalBestDao()
}
