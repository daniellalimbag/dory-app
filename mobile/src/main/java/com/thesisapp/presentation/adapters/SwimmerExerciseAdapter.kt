package com.thesisapp.presentation.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.thesisapp.R
import com.thesisapp.data.non_dao.Exercise

class SwimmerExerciseAdapter(
    private val exercises: List<Exercise>,
    private val onExerciseClick: (Exercise) -> Unit
) : RecyclerView.Adapter<SwimmerExerciseAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.exerciseCard)
        val tvExerciseName: TextView = view.findViewById(R.id.tvExerciseName)
        val tvExerciseDetails: TextView = view.findViewById(R.id.tvExerciseDetails)
        val tvEffortLevel: TextView = view.findViewById(R.id.tvEffortLevel)

        fun bind(exercise: Exercise) {
            tvExerciseName.text = exercise.name
            
            // Build details string: "6 sets × 100m - 90s rest"
            val details = buildString {
                exercise.sets?.let { append("$it sets") }
                exercise.distance?.let { 
                    if (isNotEmpty()) append(" × ")
                    append("${it}m")
                }
                exercise.restTime?.let {
                    if (isNotEmpty()) append(" - ")
                    append("${it}s rest")
                }
            }
            tvExerciseDetails.text = details.ifEmpty { exercise.description ?: "" }

            // Show effort level if available
            exercise.effortLevel?.let {
                tvEffortLevel.text = "$it%"
                tvEffortLevel.visibility = View.VISIBLE
            } ?: run {
                tvEffortLevel.visibility = View.GONE
            }

            card.setOnClickListener {
                onExerciseClick(exercise)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_swimmer_exercise, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(exercises[position])
    }

    override fun getItemCount() = exercises.size
}
