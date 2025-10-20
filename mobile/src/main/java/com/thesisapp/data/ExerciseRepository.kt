package com.thesisapp.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.thesisapp.domain.Exercise

object ExerciseRepository {
    private val items = mutableListOf<Exercise>()
    private val liveData = MutableLiveData<List<Exercise>>(items.toList())

    fun getExercises(): LiveData<List<Exercise>> = liveData

    fun add(exercise: Exercise) {
        items.add(0, exercise)
        liveData.postValue(items.toList())
    }
}
