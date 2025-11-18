package com.thesisapp.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.Exercise

class ExerciseAdapter(
    private var exercises: MutableList<Exercise>,
    private val onEditClick: (Exercise) -> Unit,
    private val onDeleteClick: (Exercise) -> Unit,
    private val readOnly: Boolean = false
) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    inner class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvExerciseName: TextView = view.findViewById(R.id.tvExerciseName)
        val tvExerciseDescription: TextView = view.findViewById(R.id.tvExerciseDescription)
        val tvExerciseDetails: TextView = view.findViewById(R.id.tvExerciseDetails)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditExercise)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteExercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = exercises[position]
        
        // Show if exercise is personal
        val exerciseName = if (exercise.teamId == -1) {
            "${exercise.name} (Personal)"
        } else {
            exercise.name
        }
        holder.tvExerciseName.text = exerciseName
        
        // Set description or hide if empty
        if (!exercise.description.isNullOrBlank()) {
            holder.tvExerciseDescription.text = exercise.description
            holder.tvExerciseDescription.visibility = View.VISIBLE
        } else {
            holder.tvExerciseDescription.visibility = View.GONE
        }

        // Build details text
        val details = buildList {
            exercise.distance?.let { if (it > 0) add("${it}m") }
            exercise.sets?.let { if (it > 0) add("$it sets") }
            exercise.restTime?.let { if (it > 0) add("${it}s rest") }
        }.joinToString(" • ")
        
        if (details.isNotBlank()) {
            holder.tvExerciseDetails.text = details
            holder.tvExerciseDetails.visibility = View.VISIBLE
        } else {
            holder.tvExerciseDetails.visibility = View.GONE
        }

        // For swimmers: only allow editing/deleting personal exercises (teamId = -1)
        // For coaches: allow editing/deleting all exercises
        if (readOnly) {
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        } else {
            val canEdit = exercise.teamId == -1 // Only personal exercises for swimmers
            holder.btnEdit.visibility = if (canEdit) View.VISIBLE else View.GONE
            holder.btnDelete.visibility = if (canEdit) View.VISIBLE else View.GONE
            if (canEdit) {
                holder.btnEdit.setOnClickListener { onEditClick(exercise) }
                holder.btnDelete.setOnClickListener { onDeleteClick(exercise) }
            }
        }
    }

    override fun getItemCount() = exercises.size

    fun updateExercises(newExercises: List<Exercise>) {
        exercises.clear()
        exercises.addAll(newExercises)
        notifyDataSetChanged()
    }
}
