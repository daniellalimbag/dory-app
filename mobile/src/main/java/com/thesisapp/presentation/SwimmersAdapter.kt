package com.thesisapp.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.Swimmer
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

class SwimmersAdapter(
    private var swimmers: MutableList<Swimmer>,
    private val onEditClick: (Swimmer) -> Unit,
    private val onDeleteClick: (Swimmer) -> Unit,
    private val onSwimmerClick: (Swimmer) -> Unit
) : RecyclerView.Adapter<SwimmersAdapter.SwimmerViewHolder>() {

    // Memoization cache for calculated ages
    private val ageCache = mutableMapOf<String, Int>()

    class SwimmerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.swimmerName)
        val age: TextView = view.findViewById(R.id.swimmerAge)
        val height: TextView = view.findViewById(R.id.swimmerHeight)
        val weight: TextView = view.findViewById(R.id.swimmerWeight)
        val sex: TextView = view.findViewById(R.id.swimmerSex)
        val wingspan: TextView = view.findViewById(R.id.swimmerWingspan)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditSwimmer)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteSwimmer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwimmerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_swimmer, parent, false)
        return SwimmerViewHolder(view)
    }

    override fun onBindViewHolder(holder: SwimmerViewHolder, position: Int) {
        val swimmer = swimmers[position]
        val context = holder.itemView.context

        holder.name.text = swimmer.name
        holder.age.text = context.getString(R.string.swimmer_age, calculateAge(swimmer.birthday))
        holder.height.text = context.getString(R.string.swimmer_height, swimmer.height)
        holder.weight.text = context.getString(R.string.swimmer_weight, swimmer.weight)
        holder.sex.text = context.getString(R.string.swimmer_sex, swimmer.sex)
        holder.wingspan.text = context.getString(R.string.swimmer_wingspan, swimmer.wingspan)

        holder.itemView.setOnClickListener {
            onSwimmerClick(swimmer)
        }

        holder.btnEdit.setOnClickListener {
            onEditClick(swimmer)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(swimmer)
        }
    }

    override fun getItemCount() = swimmers.size

    private fun calculateAge(birthday: String): Int {
        // Check cache first (memoization)
        return ageCache.getOrPut(birthday) {
            try {
                val birthDate = LocalDate.parse(birthday, DateTimeFormatter.ISO_LOCAL_DATE)
                val currentDate = LocalDate.now()
                Period.between(birthDate, currentDate).years
            } catch (e: Exception) {
                0
            }
        }
    }

    fun removeSwimmer(swimmer: Swimmer) {
        val position = swimmers.indexOf(swimmer)
        if (position != -1) {
            swimmers.removeAt(position)
            notifyItemRemoved(position)
            // Clean up cache entry
            ageCache.remove(swimmer.birthday)
        }
    }

    fun updateSwimmers(newSwimmers: List<Swimmer>) {
        swimmers.clear()
        swimmers.addAll(newSwimmers)
        // Clear cache when swimmers list is updated
        ageCache.clear()
        notifyDataSetChanged()
    }
}
