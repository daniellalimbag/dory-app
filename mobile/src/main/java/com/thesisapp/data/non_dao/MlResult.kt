package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ml_results")
data class MlResult(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Int,
    val swimmerId: Int, // link to Swimmer
    val exerciseId: Int? = null, // link to Exercise
    val date: String,
    val timeStart: String,
    val timeEnd: String,

    // Exercise details (from the exercise performed)
    val exerciseName: String? = null,
    val distance: Int? = null, // meters completed
    val sets: Int? = null, // sets completed
    val reps: Int? = null, // reps per set
    val effortLevel: String? = null, // "Easy", "Moderate", "Hard", "Max Effort"

    // New fields for Task 3
    val energyZone: String? = null, // e.g., "EN1", "SP1"
    val seasonPhase: String? = null, // e.g., "Loading", "Taper"

    // Performance metrics
    val strokeCount: Int? = null, // total strokes
    val avgStrokeLength: Float? = null, // in meters
    val strokeIndex: Float? = null, // efficiency metric (speed * stroke length)
    val avgLapTime: Float? = null, // average lap time in seconds
    val totalDistance: Int? = null, // total meters swum
    
    // Heart rate data
    val heartRateBefore: Int? = null, // BPM before session
    val heartRateAfter: Int? = null, // BPM after session
    val avgHeartRate: Int? = null, // average BPM during session
    val maxHeartRate: Int? = null, // max BPM during session
    
    // Stroke distribution
    val backstroke: Float,
    val breaststroke: Float,
    val butterfly: Float,
    val freestyle: Float,

    // Notes field
    val notes: String = ""
)
