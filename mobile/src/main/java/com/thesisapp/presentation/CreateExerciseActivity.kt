package com.thesisapp.presentation

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Exercise
import com.thesisapp.data.ExerciseCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CreateExerciseActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var tvTitle: TextView
    private lateinit var etExerciseName: TextInputEditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDistance: TextInputEditText
    private lateinit var etSets: TextInputEditText
    private lateinit var seekBarEffort: SeekBar
    private lateinit var tvEffortValue: TextView
    private lateinit var btnCreate: Button
    private lateinit var btnCancel: Button

    private var exerciseId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_exercise)

        db = AppDatabase.getInstance(this)
        exerciseId = intent.getIntExtra("EXERCISE_ID", -1)

        // Initialize views
        tvTitle = findViewById(R.id.tvTitle)
        etExerciseName = findViewById(R.id.etExerciseName)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        etDescription = findViewById(R.id.etDescription)
        etDistance = findViewById(R.id.etDistance)
        etSets = findViewById(R.id.etSets)
        seekBarEffort = findViewById(R.id.seekBarEffort)
        tvEffortValue = findViewById(R.id.tvEffortValue)
        btnCreate = findViewById(R.id.btnCreate)
        btnCancel = findViewById(R.id.btnCancel)

        // Setup category spinner
        val categories = ExerciseCategory.values().map { it.name }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, categories)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerCategory.adapter = adapter

        // Setup effort seekbar
        seekBarEffort.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvEffortValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnCancel.setOnClickListener {
            finish()
        }

        btnCreate.setOnClickListener {
            saveExercise()
        }

        // Load exercise data if editing
        if (exerciseId != -1) {
            tvTitle.text = "Edit Personal Exercise"
            btnCreate.text = "Save Changes"
            loadExerciseData()
        }
    }

    private fun loadExerciseData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val exercise = db.exerciseDao().getById(exerciseId)
            withContext(Dispatchers.Main) {
                exercise?.let {
                    etExerciseName.setText(it.name)
                    spinnerCategory.setSelection(ExerciseCategory.values().indexOf(it.category))
                    etDescription.setText(it.description ?: "")
                    etDistance.setText(it.distance.toString())
                    etSets.setText(it.sets.toString())
                    seekBarEffort.progress = it.effortLevel ?: 50
                }
            }
        }
    }

    private fun saveExercise() {
        val name = etExerciseName.text.toString().trim()
        val categoryPosition = spinnerCategory.selectedItemPosition
        val description = etDescription.text.toString().trim()
        val distance = etDistance.text.toString().toIntOrNull() ?: 0
        val sets = etSets.text.toString().toIntOrNull() ?: 1
        val effort = seekBarEffort.progress

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter an exercise name", Toast.LENGTH_SHORT).show()
            return
        }

        val category = ExerciseCategory.values()[categoryPosition]

        lifecycleScope.launch(Dispatchers.IO) {
            if (exerciseId != -1) {
                // Update existing exercise
                val existingExercise = db.exerciseDao().getById(exerciseId)
                existingExercise?.let {
                    val updatedExercise = it.copy(
                        name = name,
                        category = category,
                        description = description.ifEmpty { "Personal exercise" },
                        sets = sets,
                        distance = distance,
                        effortLevel = effort
                    )
                    db.exerciseDao().update(updatedExercise)
                }
            } else {
                // Create new exercise
                val exercise = Exercise(
                    id = 0, // Auto-generate
                    teamId = -1, // -1 indicates personal exercise
                    name = name,
                    category = category,
                    description = description.ifEmpty { "Personal exercise" },
                    sets = sets,
                    distance = distance,
                    effortLevel = effort
                )
                db.exerciseDao().insert(exercise)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@CreateExerciseActivity,
                    "Exercise created successfully!",
                    Toast.LENGTH_SHORT
                ).show()
                setResult(RESULT_OK)
                finish()
            }
        }
    }
}
