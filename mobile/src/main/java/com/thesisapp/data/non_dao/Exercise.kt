package com.thesisapp.data.non_dao

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

enum class ExerciseCategory {
    SPRINT,
    DISTANCE
}

@Parcelize
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val teamId: Int, // Exercise belongs to a team
    val name: String,
    val category: ExerciseCategory, // SPRINT or DISTANCE
    val description: String? = null,
    val distance: Int? = null, // in meters (optional)
    val sets: Int? = null,
    val restTime: Int? = null, // in seconds
    val effortLevel: Int? = null, // prescribed effort as percentage (0-100)
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
