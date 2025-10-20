package com.thesisapp.presentation.exercises

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.domain.Exercise

class ExerciseAdapter : RecyclerView.Adapter<ExerciseAdapter.VH>() {
    private val data = mutableListOf<Exercise>()

    fun submitList(list: List<Exercise>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exercise, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position])
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val day: TextView = itemView.findViewById(R.id.tvDay)
        private val focus: TextView = itemView.findViewById(R.id.tvFocus)
        private val distance: TextView = itemView.findViewById(R.id.tvDistance)
        private val interval: TextView = itemView.findViewById(R.id.tvInterval)
        private val time: TextView = itemView.findViewById(R.id.tvTime)
        private val stroke: TextView = itemView.findViewById(R.id.tvStroke)
        private val hr: TextView = itemView.findViewById(R.id.tvHr)
        private val notes: TextView = itemView.findViewById(R.id.tvNotes)
        private val components: TextView = itemView.findViewById(R.id.tvComponents)
        private val total: TextView = itemView.findViewById(R.id.tvTotal)

        fun bind(item: Exercise) {
            title.text = item.title
            day.text = item.day ?: ""
            focus.text = item.focus ?: ""
            distance.text = item.distance?.let { "$it m" } ?: "-"
            interval.text = item.interval ?: "-"
            time.text = item.time ?: "-"
            stroke.text = item.strokeCount?.toString() ?: "-"
            val hrText = listOfNotNull(
                item.preHr?.let { "Pre: $it" },
                item.postHr?.let { "Post: $it" }
            ).joinToString("  ")
            hr.text = if (hrText.isEmpty()) "-" else hrText
            if (item.notes.isNullOrBlank()) {
                notes.visibility = View.GONE
            } else {
                notes.visibility = View.VISIBLE
                notes.text = item.notes
            }

            // Components summary and total
            if (item.components.isNotEmpty()) {
                val compText = item.components.joinToString("  •  ") { c ->
                    val desc = c.description?.let { " ($it)" } ?: ""
                    "${c.energyCode}${desc}: ${c.distance}m"
                }
                components.visibility = View.VISIBLE
                components.text = compText
            } else {
                components.visibility = View.GONE
            }
            total.text = item.total?.let { "Total: ${it} m" } ?: ""
        }
    }
}
