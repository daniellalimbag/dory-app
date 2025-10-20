package com.thesisapp.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

object SessionRepository {
    private val items = mutableListOf<Session>()
    private val live = MutableLiveData<List<Session>>(emptyList())
    private var nextId = 1

    fun getSessions(): LiveData<List<Session>> = live

    @Synchronized
    fun add(session: Session) {
        val withId = if (session.id == 0) session.copy(id = nextId++) else session
        items.add(0, withId)
        live.postValue(items.toList())
    }

    fun clear() { items.clear(); live.postValue(emptyList()); nextId = 1 }
}
