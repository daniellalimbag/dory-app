package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Exercise
import com.thesisapp.data.ExerciseCategory
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoachExercisesFragment : Fragment() {

    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddExercise: ExtendedFloatingActionButton
    private lateinit var adapter: ExerciseAdapter
    private lateinit var db: AppDatabase
    
    private var currentCategory: ExerciseCategory? = null // null means "All"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coach_exercises, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getInstance(requireContext())
        categoryChipGroup = view.findViewById(R.id.categoryChipGroup)
        recyclerView = view.findViewById(R.id.exercisesRecyclerView)
        fabAddExercise = view.findViewById(R.id.fabAddExercise)

        adapter = ExerciseAdapter(
            exercises = mutableListOf(),
            onEditClick = { exercise -> showEditExerciseDialog(exercise) },
            onDeleteClick = { exercise -> showDeleteConfirmation(exercise) }
        )
        recyclerView.adapter = adapter

        // Set up category chip group
        categoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            currentCategory = when (checkedIds.firstOrNull()) {
                R.id.chipSprint -> ExerciseCategory.SPRINT
                R.id.chipDistance -> ExerciseCategory.DISTANCE
                else -> null // "All" selected
            }
            loadExercises()
        }

        fabAddExercise.setOnClickListener {
            val intent = Intent(requireContext(), AddEditExerciseActivity::class.java)
            currentCategory?.let { intent.putExtra("CATEGORY", it.name) }
            startActivity(intent)
        }

        loadExercises()
    }

    override fun onResume() {
        super.onResume()
        loadExercises()
    }

    private fun loadExercises() {
        val teamId = AuthManager.currentTeamId(requireContext()) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val exercises = if (currentCategory == null) {
                    // Show all exercises
                    db.exerciseDao().getExercisesForTeam(teamId)
                } else {
                    // Filter by category
                    db.exerciseDao().getExercisesByCategory(teamId, currentCategory!!)
                }
                withContext(Dispatchers.Main) {
                    adapter.updateExercises(exercises)
                }
            } catch (_: Exception) {
                // Handle error silently
            }
        }
    }

    private fun showEditExerciseDialog(exercise: Exercise) {
        val intent = Intent(requireContext(), AddEditExerciseActivity::class.java)
        intent.putExtra("EXERCISE_ID", exercise.id)
        startActivity(intent)
    }

    private fun showDeleteConfirmation(exercise: Exercise) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Exercise")
            .setMessage("Are you sure you want to delete ${exercise.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteExercise(exercise)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteExercise(exercise: Exercise) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.exerciseDao().delete(exercise)
                withContext(Dispatchers.Main) {
                    loadExercises()
                }
            } catch (_: Exception) {
                // Handle error silently
            }
        }
    }
}
