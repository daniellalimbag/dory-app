package com.thesisapp.data.non_dao

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class StrokeType {
    FREESTYLE,
    BACKSTROKE,
    BREASTSTROKE,
    BUTTERFLY,
    IM  // Individual Medley
}

@Parcelize
@Entity(
    tableName = "personal_bests",
    foreignKeys = [
        ForeignKey(
            entity = Swimmer::class,
            parentColumns = ["id"],
            childColumns = ["swimmerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["swimmerId", "distance", "strokeType"], unique = true)
    ]
)
@Serializable
data class PersonalBest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @SerialName("swimmer_id") val swimmerId: Int,
    val distance: Int, // in meters (e.g., 50, 100, 200, 400, 800, 1500)
    @SerialName("stroke_type") val strokeType: StrokeType,
    @SerialName("best_time") val bestTime: Float, // in seconds
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
) : Parcelable
