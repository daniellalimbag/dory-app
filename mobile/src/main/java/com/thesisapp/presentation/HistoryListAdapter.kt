package com.thesisapp.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.MlResult
import com.thesisapp.utils.animateClick

class HistoryListAdapter(
    private val onViewDetailsClick: (MlResult) -> Unit
) : ListAdapter<MlResult, HistoryListAdapter.SessionViewHolder>(Diff) {

    object Diff : DiffUtil.ItemCallback<MlResult>() {
        override fun areItemsTheSame(oldItem: MlResult, newItem: MlResult) =
            oldItem.sessionId == newItem.sessionId
        override fun areContentsTheSame(oldItem: MlResult, newItem: MlResult) = oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = getItem(position)
        holder.txtDate.text = session.date.ifBlank { "No Date" }
        holder.txtTime.text = session.timeStart.ifBlank { "No Time" }

        holder.btnViewDetails.setOnClickListener {
            it.animateClick()
            onViewDetailsClick(session)
        }
        holder.itemView.setOnClickListener {
            it.animateClick()
            onViewDetailsClick(session)
        }
    }

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtDate: TextView = itemView.findViewById(R.id.txtDate)
        val txtTime: TextView = itemView.findViewById(R.id.txtTime)
        val btnViewDetails: Button = itemView.findViewById(R.id.btnViewDetails)
    }
}