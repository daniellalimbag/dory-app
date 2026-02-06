package com.thesisapp.data.non_dao

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class ExerciseCategory {
    SPRINT,
    DISTANCE
}

@Parcelize
@Entity(tableName = "exercises")
@Serializable
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @SerialName("team_id") val teamId: Int, // Exercise belongs to a team
    val name: String,
    val category: ExerciseCategory, // SPRINT or DISTANCE
    val description: String? = null,
    val distance: Int? = null, // in meters (optional)
    val sets: Int? = null,
    @SerialName("rest_time") val restTime: Int? = null, // in seconds
    @SerialName("effort_level") val effortLevel: Int? = null, // prescribed effort as percentage (0-100)
    @SerialName("stroke_type") val strokeType: String? = null, // FREESTYLE, BACKSTROKE, BREASTSTROKE, BUTTERFLY, IM, or S1 (primary stroke)
    @SerialName("target_time") val targetTime: Float? = null, // target time per rep in seconds (auto-calculated from PB + effort)
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis()
) : Parcelable
