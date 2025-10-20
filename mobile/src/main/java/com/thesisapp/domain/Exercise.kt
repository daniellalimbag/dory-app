package com.thesisapp.domain

data class Exercise(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val distance: Int?,
    val interval: String?,
    val time: String?,
    val strokeCount: Int?,
    val preHr: Int?,
    val postHr: Int?,
    val notes: String?,
    val day: String? = null,
    val focus: String? = null,
    val components: List<ExerciseComponent> = emptyList(),
    val total: Int? = null
)

data class ExerciseComponent(
    val energyCode: String,
    val description: String?,
    val distance: Int
)
