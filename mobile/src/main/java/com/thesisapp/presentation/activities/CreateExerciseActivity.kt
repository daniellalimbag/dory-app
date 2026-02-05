package com.thesisapp.presentation.activities

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
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.ExerciseCategory
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

@AndroidEntryPoint
class CreateExerciseActivity : AppCompatActivity() {

    @Inject
    lateinit var supabase: SupabaseClient

    private lateinit var db: AppDatabase
    private lateinit var tvTitle: TextView
    private lateinit var etExerciseName: TextInputEditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDistance: TextInputEditText
    private lateinit var etSets: TextInputEditText
    private lateinit var etRestTime: TextInputEditText
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
        etRestTime = findViewById(R.id.etRestTime)
        seekBarEffort = findViewById(R.id.seekBarEffort)
        tvEffortValue = findViewById(R.id.tvEffortValue)
        btnCreate = findViewById(R.id.btnCreate)
        btnCancel = findViewById(R.id.btnCancel)

        // Setup category spinner
        val categories = ExerciseCategory.values().map { it.name }
        val adapter = ArrayAdapter(this, R.layout.spinner_item, categories)
        intent.getStringExtra("CATEGORY")?.let { cat ->
            val idx = ExerciseCategory.values().indexOfFirst { it.name == cat }
            if (idx >= 0) spinnerCategory.setSelection(idx)
        }

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

        val isCoach = AuthManager.currentUser(this)?.role == UserRole.COACH

        // Load exercise data if editing
        if (exerciseId != -1) {
            tvTitle.text = "Edit Exercise"
            btnCreate.text = "Save Changes"
            loadExerciseData()
        } else {
            tvTitle.text = if (isCoach) "Add Exercise" else "Create Personal Exercise"
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
                    etRestTime.setText((it.restTime ?: 0).toString())
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
        val restTime = etRestTime.text.toString().toIntOrNull() ?: 0
        val effort = seekBarEffort.progress

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter an exercise name", Toast.LENGTH_SHORT).show()
            return
        }

        val category = ExerciseCategory.values()[categoryPosition]

        val role = AuthManager.currentUser(this)?.role
        val isCoach = role == UserRole.COACH

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val existing = if (exerciseId != -1) db.exerciseDao().getById(exerciseId) else null
                val isExistingPersonal = existing?.teamId == -1

                val teamIdForExercise: Int = when {
                    // Swimmers always create/edit personal exercises.
                    !isCoach -> -1
                    // If editing an existing personal exercise, keep it personal.
                    isExistingPersonal -> -1
                    // Coaches creating/editing team exercises.
                    else -> AuthManager.currentTeamId(this@CreateExerciseActivity) ?: -1
                }

                // Personal exercises are stored locally only (teamId = -1).
                val isPersonal = teamIdForExercise == -1

                if (!isPersonal && teamIdForExercise <= 0) {
                    error("No team selected")
                }

                val payload = buildJsonObject {
                    put("team_id", teamIdForExercise)
                    put("name", name)
                    put("category", category.name)
                    put("description", description.ifEmpty { "Personal exercise" })
                    put("distance", distance)
                    put("sets", sets)
                    put("rest_time", restTime)
                    put("effort_level", effort)
                }

                if (exerciseId != -1) {
                    if (!isPersonal) {
                        supabase.from("exercises").update(payload) {
                            filter { eq("id", exerciseId) }
                        }
                    }

                    val existingExercise = db.exerciseDao().getById(exerciseId)
                    if (existingExercise != null) {
                        db.exerciseDao().update(
                            existingExercise.copy(
                                teamId = teamIdForExercise,
                                name = name,
                                category = category,
                                description = description.ifEmpty { "Personal exercise" },
                                sets = sets,
                                distance = distance,
                                restTime = restTime,
                                effortLevel = effort
                            )
                        )
                    }
                } else {
                    if (!isPersonal) {
                        val insertJson = supabase.from("exercises").insert(payload) { select() }.data
                        val newId = insertJson.substringAfter("\"id\":").substringBefore(',').trim().toLongOrNull()?.toInt()
                            ?: 0

                        val exercise = Exercise(
                            id = newId,
                            teamId = teamIdForExercise,
                            name = name,
                            category = category,
                            description = description.ifEmpty { "Personal exercise" },
                            sets = sets,
                            distance = distance,
                            restTime = restTime,
                            effortLevel = effort
                        )
                        db.exerciseDao().insert(exercise)
                    } else {
                        val exercise = Exercise(
                            id = 0,
                            teamId = -1,
                            name = name,
                            category = category,
                            description = description.ifEmpty { "Personal exercise" },
                            sets = sets,
                            distance = distance,
                            restTime = restTime,
                            effortLevel = effort
                        )
                        db.exerciseDao().insert(exercise)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CreateExerciseActivity,
                        "Exercise saved",
                        Toast.LENGTH_SHORT
                    ).show()
                    setResult(RESULT_OK)
                    finish()
                }
            } catch (e: Exception) {
                android.util.Log.d("DEBUG", "Supabase exercise save failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@CreateExerciseActivity,
                        e.message ?: "Failed to save exercise",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
