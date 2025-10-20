package com.thesisapp.domain

data class EnergySystem(
    val id: Long = System.currentTimeMillis(),
    var exampleWorkout: String,
    var namePurpose: String,
    var code: String,
    var effort: String,
    var typicalDistancePerSet: String,
    var repDistance: String,
    var restInterval: String,
    var totalDurationSet: String
)
