package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Exercise
import com.thesisapp.data.ExerciseCategory
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExerciseLibraryActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddExercise: ExtendedFloatingActionButton
    private lateinit var adapter: ExerciseAdapter
    private lateinit var db: AppDatabase
    
    private var currentCategory: ExerciseCategory = ExerciseCategory.SPRINT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_library)

        db = AppDatabase.getInstance(this)

        tabLayout = findViewById(R.id.tabLayout)
        recyclerView = findViewById(R.id.exercisesRecyclerView)
        fabAddExercise = findViewById(R.id.fabAddExercise)

        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }

        // Set up tabs
        tabLayout.addTab(tabLayout.newTab().setText("Sprint"))
        tabLayout.addTab(tabLayout.newTab().setText("Distance"))

        val isCoach = AuthManager.currentUser(this)?.role == com.thesisapp.utils.UserRole.COACH

        // Initialize adapter
        adapter = ExerciseAdapter(
            exercises = mutableListOf(),
            onEditClick = { exercise -> 
                if (isCoach) {
                    showEditExerciseDialog(exercise)
                } else if (exercise.teamId == -1) {
                    // Swimmers can edit personal exercises
                    editPersonalExercise(exercise)
                }
            },
            onDeleteClick = { exercise -> 
                if (isCoach) {
                    showDeleteConfirmation(exercise)
                } else if (exercise.teamId == -1) {
                    // Swimmers can delete personal exercises
                    showDeleteConfirmation(exercise)
                }
            },
            readOnly = false
        )
        recyclerView.adapter = adapter

        // Tab selection listener
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentCategory = when (tab?.position) {
                    0 -> ExerciseCategory.SPRINT
                    1 -> ExerciseCategory.DISTANCE
                    else -> ExerciseCategory.SPRINT
                }
                loadExercises()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        if (isCoach) {
            fabAddExercise.setOnClickListener {
                showAddExerciseDialog()
            }
        } else {
            // Swimmers can create personal exercises
            fabAddExercise.text = "Add Personal Exercise"
            fabAddExercise.setOnClickListener {
                val intent = Intent(this, CreateExerciseActivity::class.java)
                startActivity(intent)
            }
        }

        loadExercises()
    }

    override fun onResume() {
        super.onResume()
        loadExercises()
    }

    private fun loadExercises() {
        val teamId = AuthManager.currentTeamId(this)
        val isCoach = AuthManager.currentUser(this)?.role == com.thesisapp.utils.UserRole.COACH

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val allExercises = mutableListOf<Exercise>()
                
                if (isCoach) {
                    // Coaches only see their team's exercises
                    if (teamId != null) {
                        allExercises.addAll(db.exerciseDao().getExercisesByCategory(teamId, currentCategory))
                    }
                } else {
                    // Swimmers see team exercises + personal exercises
                    if (teamId != null) {
                        allExercises.addAll(db.exerciseDao().getExercisesByCategory(teamId, currentCategory))
                    }
                    // Add personal exercises (teamId = -1)
                    allExercises.addAll(db.exerciseDao().getExercisesByCategory(-1, currentCategory))
                }
                
                withContext(Dispatchers.Main) {
                    adapter.updateExercises(allExercises)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExerciseLibraryActivity, "Error loading exercises", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showAddExerciseDialog() {
        val intent = Intent(this, AddEditExerciseActivity::class.java)
        intent.putExtra("CATEGORY", currentCategory.name)
        startActivity(intent)
    }

    private fun showEditExerciseDialog(exercise: Exercise) {
        val intent = Intent(this, AddEditExerciseActivity::class.java)
        intent.putExtra("EXERCISE_ID", exercise.id)
        startActivity(intent)
    }

    private fun editPersonalExercise(exercise: Exercise) {
        val intent = Intent(this, CreateExerciseActivity::class.java)
        intent.putExtra("EXERCISE_ID", exercise.id)
        startActivity(intent)
    }

    private fun showDeleteConfirmation(exercise: Exercise) {
        AlertDialog.Builder(this)
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
                    Toast.makeText(this@ExerciseLibraryActivity, "${exercise.name} deleted", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ExerciseLibraryActivity, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
