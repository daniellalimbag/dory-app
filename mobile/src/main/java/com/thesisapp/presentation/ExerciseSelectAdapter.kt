package com.thesisapp.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.domain.Exercise

class ExerciseSelectAdapter : RecyclerView.Adapter<ExerciseSelectAdapter.VH>() {
    private val data = mutableListOf<Exercise>()
    private val selected = mutableSetOf<Long>()

    fun submitList(list: List<Exercise>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    fun getSelected(): List<Exercise> = data.filter { selected.contains(it.id) }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_exercise_select, parent, false)
        return VH(v, ::toggle)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position], selected.contains(data[position].id))
    }

    private fun toggle(id: Long, checked: Boolean) {
        if (checked) selected.add(id) else selected.remove(id)
    }

    class VH(itemView: View, val onToggle: (Long, Boolean) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val details: TextView = itemView.findViewById(R.id.tvDetails)
        private val check: CheckBox = itemView.findViewById(R.id.cbSelect)
        private var currentId: Long = -1

        init {
            itemView.setOnClickListener { check.isChecked = !check.isChecked }
            check.setOnCheckedChangeListener { _, isChecked -> onToggle(currentId, isChecked) }
        }

        fun bind(item: Exercise, isSelected: Boolean) {
            currentId = item.id
            title.text = item.title
            val totalText = item.total?.let { "$it m" } ?: (item.distance?.let { "$it m" } ?: "")
            val focus = item.focus ?: ""
            details.text = listOfNotNull(focus.ifBlank { null }, totalText.ifBlank { null }).joinToString(" • ")
            check.isChecked = isSelected
        }
    }
}
