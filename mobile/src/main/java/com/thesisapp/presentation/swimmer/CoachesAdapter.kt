package com.thesisapp.presentation.swimmer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R

class CoachesAdapter(
    private val onClick: (Coach) -> Unit
) : RecyclerView.Adapter<CoachesAdapter.VH>() {
    private val data = mutableListOf<Coach>()

    fun submit(list: List<Coach>) {
        data.clear()
        data.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_coach, parent, false)
        return VH(v, onClick)
    }

    override fun getItemCount(): Int = data.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(data[position])
    }

    class VH(itemView: View, val onClick: (Coach) -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val name: TextView = itemView.findViewById(R.id.tvCoachName)
        private val email: TextView = itemView.findViewById(R.id.tvCoachEmail)
        fun bind(item: Coach) {
            name.text = item.name
            email.text = item.email
            itemView.setOnClickListener { onClick(item) }
        }
    }
}
