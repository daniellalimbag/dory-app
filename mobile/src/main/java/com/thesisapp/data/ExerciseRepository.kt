package com.thesisapp.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.thesisapp.domain.Exercise
import com.thesisapp.domain.ExerciseComponent

object ExerciseRepository {
    private val items = mutableListOf<Exercise>()
    private val liveData = MutableLiveData<List<Exercise>>(items.toList())

    init {
        if (items.isEmpty()) {
            seedForLaSalle()
            liveData.value = items.toList()
        }
    }

    fun getExercises(): LiveData<List<Exercise>> = liveData

    fun add(exercise: Exercise) {
        items.add(0, exercise)
        liveData.postValue(items.toList())
    }

    private fun seedForLaSalle() {
        // Build a few sample exercises tied to Energy System codes (EN1/EN2/EN3)
        val en1 = EnergySystemRepository.findByCode("EN1")
        val en2 = EnergySystemRepository.findByCode("EN2")
        val en3 = EnergySystemRepository.findByCode("EN3")

        // EN1 Aerobic set
        items.add(
            Exercise(
                title = en1?.code ?: "EN1",
                distance = null,
                interval = null,
                time = null,
                strokeCount = null,
                preHr = null,
                postHr = null,
                notes = null,
                day = "Mon",
                focus = en1?.namePurpose ?: "Aerobic endurance",
                components = listOf(
                    ExerciseComponent(energyCode = "EN1", description = "10% / 200m easy", distance = 200),
                    ExerciseComponent(energyCode = "EN1", description = "10% / 200m easy", distance = 200),
                    ExerciseComponent(energyCode = "EN1", description = "Main set 5x400", distance = 2000)
                ),
                total = 2400
            )
        )

        // EN2 Threshold set
        items.add(
            Exercise(
                title = en2?.code ?: "EN2",
                distance = null,
                interval = null,
                time = null,
                strokeCount = null,
                preHr = null,
                postHr = null,
                notes = null,
                day = "Wed",
                focus = en2?.namePurpose ?: "Threshold",
                components = listOf(
                    ExerciseComponent(energyCode = "EN2", description = "Warm-up 400", distance = 400),
                    ExerciseComponent(energyCode = "EN2", description = "8x200 @ threshold", distance = 1600)
                ),
                total = 2000
            )
        )

        // Mixed EN3 sprint set
        items.add(
            Exercise(
                title = en3?.code ?: "EN3",
                distance = null,
                interval = null,
                time = null,
                strokeCount = null,
                preHr = null,
                postHr = null,
                notes = null,
                day = "Fri",
                focus = en3?.namePurpose ?: "VO2 / Sprint",
                components = listOf(
                    ExerciseComponent(energyCode = "EN3", description = "6x100 strong", distance = 600),
                    ExerciseComponent(energyCode = "SP1", description = "8x25 sprint", distance = 200)
                ),
                total = 800
            )
        )
    }
}
