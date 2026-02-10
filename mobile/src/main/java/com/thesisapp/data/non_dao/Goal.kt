package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class GoalType {
    SPRINT,
    ENDURANCE
}

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: String = "",
    val swimmerId: Int,
    val teamId: Int,
    val eventName: String,       // e.g., "100m Freestyle"
    val goalTime: String,        // e.g., "0:58.00"
    val startDate: Long,         // timestamp
    val endDate: Long,           // deadline timestamp
    val goalType: GoalType,      // SPRINT, ENDURANCE, THRESHOLD
    val isActive: Boolean = true
)
