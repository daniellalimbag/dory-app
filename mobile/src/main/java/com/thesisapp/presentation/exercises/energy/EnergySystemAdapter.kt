package com.thesisapp.presentation.exercises.energy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.domain.EnergySystem

class EnergySystemAdapter(
    private val onEdit: (EnergySystem) -> Unit,
    private val onDelete: (EnergySystem) -> Unit
) : RecyclerView.Adapter<EnergySystemAdapter.VH>() {
    private val data = mutableListOf<EnergySystem>()

    fun submit(list: List<EnergySystem>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_energy_system, parent, false)
        return VH(v, onEdit, onDelete)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position])
    }

    class VH(itemView: View, val onEdit: (EnergySystem) -> Unit, val onDelete: (EnergySystem) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val tvCode: TextView = itemView.findViewById(R.id.tvCode)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvEffort: TextView = itemView.findViewById(R.id.tvEffort)
        private val tvExample: TextView = itemView.findViewById(R.id.tvExample)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(item: EnergySystem) {
            tvCode.text = item.code
            tvName.text = item.namePurpose
            tvEffort.text = item.effort
            tvExample.text = item.exampleWorkout
            btnEdit.setOnClickListener { onEdit(item) }
            btnDelete.setOnClickListener { onDelete(item) }
        }
    }
}
