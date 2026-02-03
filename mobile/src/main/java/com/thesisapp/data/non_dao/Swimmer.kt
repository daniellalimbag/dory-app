package com.thesisapp.data.non_dao

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "swimmers",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"], unique = true)
    ]
)
data class Swimmer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    // Team membership moved to TeamMembership junction table
    val userId: String,
    val name: String,
    val birthday: String, // Store as "YYYY-MM-DD" format
    val height: Float, // in cm
    val weight: Float, // in kg
    val sex: String, // "Male" or "Female"
    val wingspan: Float, // in cm
    val category: ExerciseCategory = ExerciseCategory.SPRINT, // SPRINT or DISTANCE
    val specialty: String? = null // e.g., "Butterfly", "Individual Medley"
) : Parcelable
