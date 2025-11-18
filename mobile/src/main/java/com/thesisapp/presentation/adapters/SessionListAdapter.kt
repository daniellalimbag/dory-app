package com.thesisapp.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.thesisapp.R
import com.thesisapp.data.non_dao.MlResult

class SessionListAdapter(
    private var sessions: List<MlResult>,
    private val onSessionClick: (MlResult, Int) -> Unit
) : RecyclerView.Adapter<SessionListAdapter.SessionViewHolder>() {

    private var selectedPosition = 0 // Default to first item (latest)

    inner class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.sessionCard)
        val dateText: TextView = view.findViewById(R.id.tvSessionDate)
        val timeText: TextView = view.findViewById(R.id.tvSessionTime)
        val exerciseText: TextView = view.findViewById(R.id.tvExerciseName)
        val distanceText: TextView = view.findViewById(R.id.tvSessionDistance)
        val durationText: TextView = view.findViewById(R.id.tvSessionDuration)

        fun bind(session: MlResult, position: Int) {
            dateText.text = session.date
            timeText.text = session.timeStart
            
            // Use exercise name if available, otherwise fall back to dominant stroke
            exerciseText.text = if (!session.exerciseName.isNullOrBlank()) {
                session.exerciseName
            } else {
                // Fallback: determine dominant stroke
                val strokes = mapOf(
                    "Freestyle" to session.freestyle,
                    "Backstroke" to session.backstroke,
                    "Breaststroke" to session.breaststroke,
                    "Butterfly" to session.butterfly
                )
                val dominantStroke = strokes.maxByOrNull { it.value }?.key ?: "Mixed"
                "$dominantStroke Session"
            }
            
            // Display distance if available
            distanceText.text = session.totalDistance?.let { "$it m" } ?: "-- m"
            durationText.text = session.timeEnd
            
            // Highlight selected item
            if (position == selectedPosition) {
                card.strokeWidth = 4
                card.strokeColor = itemView.context.getColor(R.color.primary)
            } else {
                card.strokeWidth = 0
            }
            
            // Click handler
            card.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = position
                notifyItemChanged(oldPosition)
                notifyItemChanged(position)
                onSessionClick(session, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_swimmer_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        holder.bind(sessions[position], position)
    }

    override fun getItemCount() = sessions.size

    fun updateSessions(newSessions: List<MlResult>) {
        sessions = newSessions
        selectedPosition = 0 // Reset to first item
        notifyDataSetChanged()
    }
}
