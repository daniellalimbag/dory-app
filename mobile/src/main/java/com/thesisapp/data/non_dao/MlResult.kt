package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Entity(tableName = "ml_results")
@Serializable
data class MlResult(
    @PrimaryKey(autoGenerate = true)
    @SerialName("session_id") val sessionId: Int,
    @SerialName("swimmer_id") val swimmerId: Int, // link to Swimmer
    @SerialName("exercise_id") val exerciseId: Int? = null, // link to Exercise
    val date: String,
    @SerialName("time_start") val timeStart: String,
    @SerialName("time_end") val timeEnd: String,

    // Exercise details (from the exercise performed)
    @SerialName("exercise_name") val exerciseName: String? = null,
    val distance: Int? = null, // meters completed
    val sets: Int? = null, // sets completed
    val reps: Int? = null, // reps per set
    @SerialName("effort_level") val effortLevel: String? = null, // "Easy", "Moderate", "Hard", "Max Effort"

    // New fields for Task 3
    @SerialName("energy_zone") val energyZone: String? = null, // e.g., "EN1", "SP1"
    @SerialName("season_phase") val seasonPhase: String? = null, // e.g., "Loading", "Taper"

    // Performance metrics
    @SerialName("stroke_count") val strokeCount: Int? = null, // total strokes
    @SerialName("avg_stroke_length") val avgStrokeLength: Float? = null, // in meters
    @SerialName("stroke_index") val strokeIndex: Float? = null, // efficiency metric (speed * stroke length)
    @SerialName("avg_lap_time") val avgLapTime: Float? = null, // average lap time in seconds
    @SerialName("total_distance") val totalDistance: Int? = null, // total meters swum
    
    // Heart rate data
    @SerialName("heart_rate_before") val heartRateBefore: Int? = null, // BPM before session
    @SerialName("heart_rate_after") val heartRateAfter: Int? = null, // BPM after session
    @SerialName("avg_heart_rate") val avgHeartRate: Int? = null, // average BPM during session
    @SerialName("max_heart_rate") val maxHeartRate: Int? = null, // max BPM during session
    
    // Stroke distribution
    val backstroke: Float,
    val breaststroke: Float,
    val butterfly: Float,
    val freestyle: Float,

    // Notes field
    val notes: String = ""
)
