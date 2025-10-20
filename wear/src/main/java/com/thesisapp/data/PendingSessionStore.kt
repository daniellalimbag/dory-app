package com.thesisapp.data

import android.content.Context
import android.util.Log

class PendingSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun addSession(sessionId: Int) {
        updateSessionSet { current ->
            if (current.contains(sessionId.toString())) {
                Log.d(TAG, "Session $sessionId already marked as pending")
                current
            } else {
                current + sessionId.toString()
            }
        }
    }

    fun removeSession(sessionId: Int) {
        updateSessionSet { current -> current - sessionId.toString() }
    }

    fun containsSession(sessionId: Int): Boolean {
        return getPendingSessions().contains(sessionId)
    }

    fun hasPendingSessions(): Boolean {
        return getPendingSessions().isNotEmpty()
    }

    fun getPendingSessions(): Set<Int> {
        val stored = prefs.getStringSet(KEY_SESSIONS, emptySet()) ?: emptySet()
        return stored.mapNotNull { it.toIntOrNull() }.toSet()
    }

    private fun updateSessionSet(transform: (Set<String>) -> Set<String>) {
        val current = prefs.getStringSet(KEY_SESSIONS, emptySet())?.toSet() ?: emptySet()
        val updated = transform(current)
        prefs.edit().putStringSet(KEY_SESSIONS, updated).apply()
    }

    companion object {
        private const val PREF_NAME = "pending_sessions"
        private const val KEY_SESSIONS = "session_ids"
        private const val TAG = "PendingSessionStore"
    }
}
