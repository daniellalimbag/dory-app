package com.thesisapp.utils

import android.content.Context
import com.thesisapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extension functions for populating database with dummy data for testing
 */
suspend fun AppDatabase.populateDummyData(context: Context, teamId: Int) {
    withContext(Dispatchers.IO) {
        // Check if we already have swimmers for this team
        val teamMembers = teamMembershipDao().getSwimmersForTeam(teamId)
        
        // Generate and insert exercises first
        var exercises = exerciseDao().getExercisesForTeam(teamId)
        if (exercises.isEmpty()) {
            val newExercises = DummyDataGenerator.generateDummyExercises(teamId)
            newExercises.forEach { exercise ->
                val exerciseId = exerciseDao().insert(exercise).toInt()
                // Update exercise list with actual IDs
                exercises = exerciseDao().getExercisesForTeam(teamId)
            }
        }
        
        // Only populate swimmers if team has none yet
        if (teamMembers.isEmpty()) {
            // Generate 5 dummy swimmers
            val swimmers = DummyDataGenerator.generateDummySwimmers(teamId, 5)
            
            swimmers.forEach { swimmer ->
                // Insert swimmer
                val swimmerId = swimmerDao().insertSwimmer(swimmer).toInt()
                
                // Add to team
                teamMembershipDao().insert(
                    TeamMembership(
                        swimmerId = swimmerId,
                        teamId = teamId
                    )
                )
                
                // Generate 8-15 sessions for each swimmer with varied count
                val sessionCount = (8..15).random()
                val sessions = DummyDataGenerator.generateDummySessions(
                    swimmerId = swimmerId,
                    swimmerCategory = swimmer.category,
                    exercises = exercises,
                    count = sessionCount
                )
                
                sessions.forEach { session ->
                    mlResultDao().insert(session)
                }
            }
        }
    }
}

/**
 * Populate dummy data for a specific swimmer
 */
suspend fun AppDatabase.populateDummyDataForSwimmer(
    swimmerId: Int,
    swimmerCategory: ExerciseCategory,
    teamId: Int,
    sessionCount: Int = 10
) {
    withContext(Dispatchers.IO) {
        val exercises = exerciseDao().getExercisesForTeam(teamId)
        val sessions = DummyDataGenerator.generateDummySessions(
            swimmerId = swimmerId,
            swimmerCategory = swimmerCategory,
            exercises = exercises,
            count = sessionCount
        )
        sessions.forEach { session ->
            mlResultDao().insert(session)
        }
    }
}

/**
 * Clear all data from database (for testing)
 */
suspend fun AppDatabase.clearAllData() {
    withContext(Dispatchers.IO) {
        clearAllTables()
    }
}
