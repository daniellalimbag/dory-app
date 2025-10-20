package com.thesisapp.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.thesisapp.domain.EnergySystem

object EnergySystemRepository {
    private val items = mutableListOf<EnergySystem>()
    private val liveData = MutableLiveData<List<EnergySystem>>()

    init {
        if (items.isEmpty()) {
            seed()
        }
        liveData.value = items.toList()
    }

    private fun seed() {
        items.addAll(
            listOf(
                EnergySystem(
                    exampleWorkout = "10x200 @ moderate pace, 4x400 smooth aerobic",
                    namePurpose = "Aerobic Maintenance / Recovery",
                    code = "EN1",
                    effort = "65–75%",
                    typicalDistancePerSet = "1500–3000m",
                    repDistance = "100–400m",
                    restInterval = "10–20s",
                    totalDurationSet = "20–40 mins"
                ),
                EnergySystem(
                    exampleWorkout = "8x200 @ threshold pace, 6x300 steady effort",
                    namePurpose = "Aerobic Threshold",
                    code = "EN2",
                    effort = "80–85%",
                    typicalDistancePerSet = "1200–2400m",
                    repDistance = "100–300m",
                    restInterval = "15–30s",
                    totalDurationSet = "20–35 mins"
                ),
                EnergySystem(
                    exampleWorkout = "6x200 strong @ race pace, 8x100 fast aerobic",
                    namePurpose = "Lactate Endurance / VO₂ max",
                    code = "EN3",
                    effort = "90–95%",
                    typicalDistancePerSet = "600–1200m",
                    repDistance = "100–200m",
                    restInterval = "15–25s",
                    totalDurationSet = "15–25 mins"
                ),
                EnergySystem(
                    exampleWorkout = "12×25m from dive, 8×15m sprints",
                    namePurpose = "Speed / Neuromuscular Efficiency",
                    code = "SP1",
                    effort = "Max effort",
                    typicalDistancePerSet = "600–1200m",
                    repDistance = "15–25m",
                    restInterval = "1:00–2:00",
                    totalDurationSet = "10–20 mins"
                )
            )
        )
    }

    fun getEnergySystems(): LiveData<List<EnergySystem>> = liveData

    fun add(item: EnergySystem) {
        items.add(0, item)
        liveData.postValue(items.toList())
    }

    fun update(item: EnergySystem) {
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx != -1) {
            items[idx] = item
            liveData.postValue(items.toList())
        }
    }

    fun delete(id: Long) {
        items.removeAll { it.id == id }
        liveData.postValue(items.toList())
    }

    fun findByCode(code: String): EnergySystem? = items.firstOrNull { it.code.equals(code, true) }
}
