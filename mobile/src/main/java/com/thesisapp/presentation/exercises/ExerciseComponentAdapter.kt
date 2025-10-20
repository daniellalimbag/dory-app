package com.thesisapp.presentation.exercises

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.domain.ExerciseComponent

class ExerciseComponentAdapter : RecyclerView.Adapter<ExerciseComponentAdapter.VH>() {
    private val data = mutableListOf<ExerciseComponent>()

    fun setItems(items: List<ExerciseComponent>) {
        data.clear()
        data.addAll(items)
        notifyDataSetChanged()
    }

    fun addItem(item: ExerciseComponent) {
        data.add(item)
        notifyItemInserted(data.size - 1)
    }

    fun items(): List<ExerciseComponent> = data.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_exercise_component, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position])
    }

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val code: TextView = itemView.findViewById(R.id.tvCompCode)
        private val desc: TextView = itemView.findViewById(R.id.tvCompDesc)
        private val dist: TextView = itemView.findViewById(R.id.tvCompDistance)

        fun bind(item: ExerciseComponent) {
            code.text = item.energyCode
            desc.text = item.description ?: ""
            dist.text = "${item.distance} m"
        }
    }
}
