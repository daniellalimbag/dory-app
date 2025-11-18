package com.thesisapp.presentation.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.non_dao.MlResult
import com.thesisapp.presentation.activities.CategorizeSessionActivity
import java.text.SimpleDateFormat
import java.util.*

class SessionAdapter(
    private val sessions: List<MlResult>,
    private val onSessionClick: (MlResult) -> Unit
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session_enhanced, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        holder.bind(session, onSessionClick)
    }

    override fun getItemCount() = sessions.size

    class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSessionDate: TextView = itemView.findViewById(R.id.tvSessionDate)
        private val tvExerciseName: TextView = itemView.findViewById(R.id.tvExerciseName)
        private val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val btnCategorize: Button? = itemView.findViewById(R.id.btnCategorize)

        fun bind(session: MlResult, onClick: (MlResult) -> Unit) {
            // Format date
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvSessionDate.text = try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = sdf.parse(session.date)
                date?.let { dateFormat.format(it) } ?: session.date
            } catch (e: Exception) {
                session.date
            }

            // Exercise name or "Uncategorized"
            val isUncategorized = session.exerciseName == null || session.exerciseName == "Uncategorized"
            tvExerciseName.text = if (isUncategorized) "⚠️ Uncategorized" else session.exerciseName

            // Show categorize button if uncategorized
            btnCategorize?.visibility = if (isUncategorized) View.VISIBLE else View.GONE
            btnCategorize?.setOnClickListener {
                val intent = Intent(itemView.context, CategorizeSessionActivity::class.java)
                intent.putExtra("sessionId", session.sessionId)
                itemView.context.startActivity(intent)
            }

            // Distance
            tvDistance.text = session.totalDistance?.let { "${it}m" } ?: "--"

            // Duration
            tvDuration.text = calculateDuration(session.timeStart, session.timeEnd)

            // Only allow clicking to view details if categorized
            if (isUncategorized) {
                itemView.setOnClickListener(null)
                itemView.isClickable = false
            } else {
                itemView.setOnClickListener { onClick(session) }
                itemView.isClickable = true
            }
        }

        private fun calculateDuration(startTime: String, endTime: String): String {
            return try {
                val format = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val start = format.parse(startTime)
                val end = format.parse(endTime)
                if (start != null && end != null) {
                    val durationMillis = end.time - start.time
                    val minutes = (durationMillis / 1000 / 60).toInt()
                    "${minutes} min"
                } else {
                    "--"
                }
            } catch (e: Exception) {
                "--"
            }
        }
    }
}
