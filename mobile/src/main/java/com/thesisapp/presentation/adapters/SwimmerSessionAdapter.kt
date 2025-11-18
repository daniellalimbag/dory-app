package com.thesisapp.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.thesisapp.R
import com.thesisapp.data.non_dao.MlResult

class SwimmerSessionAdapter(
    private val sessions: List<MlResult>,
    private val onSessionClick: (MlResult) -> Unit
) : RecyclerView.Adapter<SwimmerSessionAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.sessionCard)
        val tvExerciseName: TextView = view.findViewById(R.id.tvExerciseName)
        val tvSessionDate: TextView = view.findViewById(R.id.tvSessionDate)
        val tvSessionTime: TextView = view.findViewById(R.id.tvSessionTime)
        val tvSessionDistance: TextView = view.findViewById(R.id.tvSessionDistance)

        fun bind(session: MlResult) {
            tvExerciseName.text = session.exerciseName ?: "Session"
            tvSessionDate.text = session.date
            tvSessionTime.text = "${session.timeStart} - ${session.timeEnd}"
            tvSessionDistance.text = session.totalDistance?.let { "$it m" } ?: "--"

            card.setOnClickListener {
                onSessionClick(session)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_swimmer_session_simple, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sessions[position])
    }

    override fun getItemCount() = sessions.size
}
