package com.thesisapp.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "swimmers")
data class Swimmer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    // Team membership moved to TeamMembership junction table
    val name: String,
    val birthday: String, // Store as "YYYY-MM-DD" format
    val height: Float, // in cm
    val weight: Float, // in kg
    val sex: String, // "Male" or "Female"
    val wingspan: Float, // in cm
    val category: ExerciseCategory = ExerciseCategory.SPRINT // SPRINT or DISTANCE
) : Parcelable
