package com.thesisapp.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.Session

class SessionsAdapter(
    private var sessions: List<Session>,
    private val onSessionClick: (Session) -> Unit
) : RecyclerView.Adapter<SessionsAdapter.SessionViewHolder>() {

    class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val date: TextView = view.findViewById(R.id.sessionDate)
        val time: TextView = view.findViewById(R.id.sessionTime)
        val swimmer: TextView = view.findViewById(R.id.sessionSwimmer)
        val btnViewDetails: Button = view.findViewById(R.id.btnViewDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]

        holder.date.text = session.date
        holder.time.text = session.time
        holder.swimmer.text = session.swimmerName

        holder.itemView.setOnClickListener {
            onSessionClick(session)
        }

        holder.btnViewDetails.setOnClickListener {
            onSessionClick(session)
        }
    }

    override fun getItemCount() = sessions.size

    fun updateSessions(newSessions: List<Session>) {
        sessions = newSessions
        notifyDataSetChanged()
    }
}
