package com.thesisapp.data.non_dao

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "goal_progress")
data class GoalProgress(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val clientId: String = "",
    val goalId: Int,
    val date: Long,                    // timestamp
    val projectedRaceTime: String,     // calculated e.g., "0:59.50"
    val sessionId: Int?                // reference to logged exercise (nullable)
)
