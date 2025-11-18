package com.thesisapp.presentation.activities

import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.ExerciseCategory
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddEditExerciseActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var etExerciseName: EditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var etDescription: EditText
    private lateinit var etDistance: EditText
    private lateinit var etSets: EditText
    private lateinit var etRestTime: EditText
    private lateinit var btnSave: MaterialButton
    private lateinit var db: AppDatabase
    
    private var exerciseId: Int? = null
    private var currentExercise: Exercise? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_exercise)

        db = AppDatabase.getInstance(this)

        tvTitle = findViewById(R.id.tvTitle)
        etExerciseName = findViewById(R.id.etExerciseName)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        etDescription = findViewById(R.id.etDescription)
        etDistance = findViewById(R.id.etDistance)
        etSets = findViewById(R.id.etSets)
        etRestTime = findViewById(R.id.etRestTime)
        btnSave = findViewById(R.id.btnSave)

        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener { finish() }

        // Set up category spinner
        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("Sprint", "Distance")
        )
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = categoryAdapter

        // Check if editing existing exercise
        exerciseId = intent.getIntExtra("EXERCISE_ID", -1).takeIf { it != -1 }
        val categoryFromIntent = intent.getStringExtra("CATEGORY")

        if (exerciseId != null) {
            tvTitle.text = "Edit Exercise"
            btnSave.text = "Save Changes"
            loadExercise(exerciseId!!)
        } else {
            tvTitle.text = "Add Exercise"
            btnSave.text = "Add Exercise"
            
            // Pre-select category if passed from library
            categoryFromIntent?.let {
                val position = when (it) {
                    "SPRINT" -> 0
                    "DISTANCE" -> 1
                    else -> 0
                }
                spinnerCategory.setSelection(position)
            }
        }

        btnSave.setOnClickListener {
            saveExercise()
        }
    }

    private fun loadExercise(id: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val exercise = db.exerciseDao().getExerciseById(id)
                withContext(Dispatchers.Main) {
                    exercise?.let {
                        currentExercise = it
                        populateFields(it)
                    } ?: run {
                        Toast.makeText(this@AddEditExerciseActivity, "Exercise not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddEditExerciseActivity, "Error loading exercise", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun populateFields(exercise: Exercise) {
        etExerciseName.setText(exercise.name)
        spinnerCategory.setSelection(
            when (exercise.category) {
                ExerciseCategory.SPRINT -> 0
                ExerciseCategory.DISTANCE -> 1
            }
        )
        etDescription.setText(exercise.description)
        exercise.distance?.let { if (it > 0) etDistance.setText(it.toString()) }
        exercise.sets?.let { if (it > 0) etSets.setText(it.toString()) }
        exercise.restTime?.let { if (it > 0) etRestTime.setText(it.toString()) }
    }

    private fun saveExercise() {
        val name = etExerciseName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, "Exercise name is required", Toast.LENGTH_SHORT).show()
            return
        }

        val teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            Toast.makeText(this, "No team selected", Toast.LENGTH_SHORT).show()
            return
        }

        val category = when (spinnerCategory.selectedItemPosition) {
            0 -> ExerciseCategory.SPRINT
            1 -> ExerciseCategory.DISTANCE
            else -> ExerciseCategory.SPRINT
        }

        val description = etDescription.text.toString().trim()
        val distance = etDistance.text.toString().toIntOrNull() ?: 0
        val sets = etSets.text.toString().toIntOrNull() ?: 0
        val restTime = etRestTime.text.toString().toIntOrNull() ?: 0

        val exercise = Exercise(
            id = exerciseId ?: 0,
            teamId = teamId,
            name = name,
            category = category,
            description = description,
            distance = distance,
            sets = sets,
            restTime = restTime,
            createdAt = currentExercise?.createdAt ?: System.currentTimeMillis()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (exerciseId != null) {
                    db.exerciseDao().update(exercise)
                } else {
                    db.exerciseDao().insert(exercise)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddEditExerciseActivity, "Exercise saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AddEditExerciseActivity, "Error saving exercise", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Hide keyboard when touching outside of EditText
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val view = currentFocus
            if (view != null) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}
