package com.thesisapp.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.Session
import com.thesisapp.utils.animateClick

class HistoryListAdapter(
    private var sessions: List<Session>,
    private val onViewDetailsClick: (Session) -> Unit
) : RecyclerView.Adapter<HistoryListAdapter.SessionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]

        holder.txtDate.text = session.date
        holder.txtTime.text = session.time
        holder.txtSwimmer.text = session.swimmerName

        holder.btnViewDetails.setOnClickListener {
            it.animateClick()
            onViewDetailsClick(session)
        }

        holder.itemView.setOnClickListener {
            it.animateClick()
            onViewDetailsClick(session)
        }
    }

    override fun getItemCount(): Int = sessions.size

    fun updateSessions(newSessions: List<Session>) {
        sessions = newSessions
        notifyDataSetChanged()
    }

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        val txtSwimmer: TextView = itemView.findViewById(R.id.txtSwimmer)
        val btnViewDetails: Button = itemView.findViewById(R.id.btnViewDetails)
    }
}