package com.thesisapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ml_results")
data class MlResult(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Int,
    val date: String,
    val timeStart: String,
    val timeEnd: String,

    // Stroke distribution
    val backstroke: Float,
    val breaststroke: Float,
    val butterfly: Float,
    val freestyle: Float,

    // Notes field
    val notes: String = ""
)