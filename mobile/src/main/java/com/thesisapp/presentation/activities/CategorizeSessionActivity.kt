package com.thesisapp.presentation.activities

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.ExerciseCategory
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategorizeSessionActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var tvTitle: TextView
    private lateinit var spinnerContext: Spinner
    private lateinit var spinnerExercise: Spinner
    private lateinit var btnSave: Button
    private lateinit var btnSkip: Button

    private var sessionId: Int = -1
    private var contexts = mutableListOf<Pair<String, Int?>>() // (name, teamId or null for personal)
    private var exercises = mutableListOf<Exercise>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categorize_session)

        db = AppDatabase.getInstance(this)
        sessionId = intent.getIntExtra("sessionId", -1)

        if (sessionId == -1) {
            Toast.makeText(this, "Invalid session", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tvTitle = findViewById(R.id.tvTitle)
        spinnerContext = findViewById(R.id.spinnerContext)
        spinnerExercise = findViewById(R.id.spinnerExercise)
        btnSave = findViewById(R.id.btnSave)
        btnSkip = findViewById(R.id.btnSkip)

        tvTitle.text = "Categorize Your Session"

        // Setup context change listener
        spinnerContext.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadExercisesForContext(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSkip.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveCategorization()
        }

        loadContexts()
    }

    private fun loadContexts() {
        lifecycleScope.launch(Dispatchers.IO) {
            contexts.clear()
            
            // Add "Personal" option
            contexts.add(Pair("Personal", null))
            
            // Add current team if exists
            val teamId = AuthManager.currentTeamId(this@CategorizeSessionActivity)
            if (teamId != null) {
                val team = db.teamDao().getById(teamId)
                team?.let {
                    contexts.add(Pair(it.name, it.id))
                }
            }

            withContext(Dispatchers.Main) {
                val contextNames = contexts.map { it.first }
                val adapter = ArrayAdapter(
                    this@CategorizeSessionActivity,
                    R.layout.spinner_item,
                    contextNames
                )
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                spinnerContext.adapter = adapter
                
                // Load exercises for first context
                if (contexts.isNotEmpty()) {
                    loadExercisesForContext(0)
                }
            }
        }
    }

    private fun loadExercisesForContext(contextPosition: Int) {
        if (contextPosition < 0 || contextPosition >= contexts.size) return
        
        val selectedContext = contexts[contextPosition]
        
        lifecycleScope.launch(Dispatchers.IO) {
            exercises.clear()

            // Get current swimmer from activity intent or default to SPRINT
            val swimmerId = intent.getIntExtra("SWIMMER_ID", -1)
            val swimmer = if (swimmerId != -1) db.swimmerDao().getById(swimmerId) else null
            val swimmerCategory = swimmer?.category ?: ExerciseCategory.SPRINT
            
            // Load exercises filtered by context and swimmer category
            val allExercises = if (selectedContext.second == null) {
                // Personal context: load personal exercises
                db.exerciseDao().getExercisesForTeam(-1)
            } else {
                // Team context: load team exercises
                db.exerciseDao().getExercisesForTeam(selectedContext.second!!)
            }
            
            // Filter by swimmer's category
            exercises.addAll(allExercises.filter { it.category == swimmerCategory })

            // Always add "General Training" option at the top
            exercises.add(0, Exercise(
                id = -1,
                teamId = -1,
                name = "General Training",
                category = swimmerCategory,
                description = "General swim training",
                sets = 1,
                distance = 0,
                effortLevel = 50
            ))

            withContext(Dispatchers.Main) {
                val exerciseNames = exercises.map { it.name }
                val adapter = ArrayAdapter(
                    this@CategorizeSessionActivity,
                    R.layout.spinner_item,
                    exerciseNames
                )
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                spinnerExercise.adapter = adapter
            }
        }
    }

    private fun saveCategorization() {
        val exercisePosition = spinnerExercise.selectedItemPosition

        if (exercisePosition < 0) {
            Toast.makeText(this, "Please select an exercise", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedExercise = exercises[exercisePosition]

        lifecycleScope.launch(Dispatchers.IO) {
            // Update the session with categorization
            val session = db.mlResultDao().getBySessionId(sessionId)
            if (session != null) {
                val updated = session.copy(
                    exerciseId = selectedExercise.id, // Use the actual ID (including -1 for General Training)
                    exerciseName = selectedExercise.name
                )
                db.mlResultDao().update(updated)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CategorizeSessionActivity, "Session categorized!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
