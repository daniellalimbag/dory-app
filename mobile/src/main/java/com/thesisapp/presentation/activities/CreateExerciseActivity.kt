package com.thesisapp.presentation.activities

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.view.View
import android.widget.AdapterView
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
    private lateinit var spinnerStrokeType: Spinner
    private lateinit var tvSelectedSwimmer: TextView
    private lateinit var tvTargetTime: TextView
    private lateinit var btnCreate: Button
    private lateinit var btnCancel: Button

    private var exerciseId: Int = -1
    private var currentSwimmerId: Int? = null

    private val effortZones = listOf("REC", "EN1", "EN2", "EN3", "SP1", "SP2", "SP3")
    private val strokeTypes = listOf("S1 (Primary)", "FREESTYLE", "BACKSTROKE", "BREASTSTROKE", "BUTTERFLY", "IM")

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
        spinnerStrokeType = findViewById(R.id.spinnerStrokeType)
        tvSelectedSwimmer = findViewById(R.id.tvSelectedSwimmer)
        tvTargetTime = findViewById(R.id.tvTargetTime)
        btnCreate = findViewById(R.id.btnCreate)
        btnCancel = findViewById(R.id.btnCancel)

        // Setup category spinner with custom adapter that forces text color
        val categories = ExerciseCategory.values().map { it.name }
        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, categories) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.setTextColor(getColor(R.color.text))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.setTextColor(getColor(R.color.text))
                return view
            }
        }
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerCategory.adapter = adapter

        intent.getStringExtra("CATEGORY")?.let { cat ->
            val idx = ExerciseCategory.values().indexOfFirst { it.name == cat }
            if (idx >= 0) spinnerCategory.setSelection(idx)
        }

        // Setup stroke type spinner
        val strokeAdapter = object : ArrayAdapter<String>(this, R.layout.spinner_item, strokeTypes) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as? TextView)?.setTextColor(getColor(R.color.text))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as? TextView)?.setTextColor(getColor(R.color.text))
                return view
            }
        }
        strokeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerStrokeType.adapter = strokeAdapter

        spinnerStrokeType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                (view as? TextView)?.setTextColor(getColor(R.color.text))
                calculateTargetTime()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup effort seekbar
        seekBarEffort.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvEffortValue.text = effortZones.getOrNull(progress) ?: effortZones.first()
                calculateTargetTime()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        tvEffortValue.text = effortZones.getOrNull(seekBarEffort.progress) ?: effortZones.first()

        // Add text change listeners for distance to recalculate target time
        etDistance.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                calculateTargetTime()
            }
        })

        btnCancel.setOnClickListener {
            finish()
        }

        btnCreate.setOnClickListener {
            saveExercise()
        }

        val isCoach = AuthManager.currentUser(this)?.role == UserRole.COACH

        tvTargetTime.setOnClickListener {
            if (isCoach && currentSwimmerId == null) {
                openSwimmerPicker()
            }
        }

        if (!isCoach) {
            tvSelectedSwimmer.visibility = View.GONE
        } else {
            tvSelectedSwimmer.text = "No swimmer selected"
            tvSelectedSwimmer.setTextColor(getColor(R.color.text_secondary))

            tvSelectedSwimmer.setOnClickListener {
                openSwimmerPicker()
            }

            // Preselect swimmer if passed in
            val preselected = intent.getIntExtra("SWIMMER_ID", -1).takeIf { it > 0 }
            if (preselected != null) {
                currentSwimmerId = preselected
                lifecycleScope.launch(Dispatchers.IO) {
                    val swimmer = db.swimmerDao().getById(preselected)
                    withContext(Dispatchers.Main) {
                        tvSelectedSwimmer.text = swimmer?.name ?: "Swimmer #$preselected"
                        tvSelectedSwimmer.setTextColor(getColor(R.color.text))
                        calculateTargetTime()
                    }
                }
            }
        }

        // Load exercise data if editing
        if (exerciseId != -1) {
            tvTitle.text = "Edit Exercise"
            btnCreate.text = "Save Changes"
            loadExerciseData()
        } else {
            tvTitle.text = if (isCoach) "Add Exercise" else "Create Personal Exercise"
        }
    }

    private fun openSwimmerPicker() {
        lifecycleScope.launch(Dispatchers.IO) {
            val teamId = AuthManager.currentTeamId(this@CreateExerciseActivity)
            if (teamId == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CreateExerciseActivity, "No team selected", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val swimmers = db.teamMembershipDao().getSwimmersForTeam(teamId)
            if (swimmers.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CreateExerciseActivity, "No swimmers in this team", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val names = swimmers.map { it.name }.toTypedArray()
            val currentIndex = currentSwimmerId?.let { id -> swimmers.indexOfFirst { it.id == id } } ?: -1

            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@CreateExerciseActivity)
                    .setTitle("Select swimmer")
                    .setSingleChoiceItems(names, currentIndex) { dialog, which ->
                        val selected = swimmers[which]
                        currentSwimmerId = selected.id
                        tvSelectedSwimmer.text = selected.name
                        tvSelectedSwimmer.setTextColor(getColor(R.color.text))
                        calculateTargetTime()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
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
                    seekBarEffort.progress = percentToEffortZoneIndex(it.effortLevel)
                    tvEffortValue.text = effortZones.getOrNull(seekBarEffort.progress) ?: effortZones.first()
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
        val effort = effortZoneIndexToPercent(seekBarEffort.progress)
        val strokeType = strokeTypes.getOrNull(spinnerStrokeType.selectedItemPosition)
        val targetTimeText = tvTargetTime.text.toString()
        val targetTime = if (targetTimeText.contains(":")) {
            try {
                val parts = targetTimeText.split(":")
                val minutes = parts[0].toFloat()
                val seconds = parts[1].toFloat()
                minutes * 60 + seconds
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

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

                val currentTeamId = AuthManager.currentTeamId(this@CreateExerciseActivity)
                
                val teamIdForExercise: Int = when {
                    // Swimmers always create/edit personal exercises.
                    !isCoach -> -1
                    // If editing an existing personal exercise, keep it personal.
                    isExistingPersonal -> -1
                    // Coaches with no team selected create personal exercises.
                    currentTeamId == null -> -1
                    // Coaches creating/editing team exercises.
                    else -> currentTeamId
                }

                // Personal exercises are stored locally only (teamId = -1).
                val isPersonal = teamIdForExercise == -1

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
                                effortLevel = effort,
                                strokeType = strokeType,
                                targetTime = targetTime
                            )
                        )
                    }
                } else {
                    if (!isPersonal) {
                        try {
                            val insertJson = supabase.from("exercises").insert(payload) { select() }.data
                            val newId = insertJson.substringAfter("\"id\":").substringBefore(',').trim().toLongOrNull()?.toInt()
                                ?: 0

                            val exercise = Exercise(
                                id = newId,
                                teamId = teamIdForExercise,
                                name = name,
                                category = category,
                                description = description.ifEmpty { "Team exercise" },
                                sets = sets,
                                distance = distance,
                                restTime = restTime,
                                effortLevel = effort,
                                strokeType = strokeType,
                                targetTime = targetTime
                            )
                            db.exerciseDao().insert(exercise)
                        } catch (e: Exception) {
                            android.util.Log.e("CreateExercise", "Supabase insert failed, saving locally only", e)
                            // Fallback: save as personal exercise if Supabase fails
                            val exercise = Exercise(
                                id = 0,
                                teamId = -1,
                                name = name,
                                category = category,
                                description = description.ifEmpty { "Personal exercise" },
                                sets = sets,
                                distance = distance,
                                restTime = restTime,
                                effortLevel = effort,
                                strokeType = strokeType,
                                targetTime = targetTime
                            )
                            db.exerciseDao().insert(exercise)
                        }
                    } else {
                        // Personal exercise - local only
                        val exercise = Exercise(
                            id = 0,
                            teamId = -1,
                            name = name,
                            category = category,
                            description = description.ifEmpty { "Personal exercise" },
                            sets = sets,
                            distance = distance,
                            restTime = restTime,
                            effortLevel = effort,
                            strokeType = strokeType,
                            targetTime = targetTime
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

    private fun percentToEffortZoneIndex(percent: Int?): Int {
        val p = percent ?: 0
        return when {
            p <= 10 -> 0 // REC
            p <= 30 -> 1 // EN1
            p <= 50 -> 2 // EN2
            p <= 70 -> 3 // EN3
            p <= 80 -> 4 // SP1
            p <= 90 -> 5 // SP2
            else -> 6 // SP3
        }
    }

    private fun effortZoneIndexToPercent(index: Int): Int {
        return when (index.coerceIn(0, effortZones.lastIndex)) {
            0 -> 10
            1 -> 30
            2 -> 50
            3 -> 70
            4 -> 80
            5 -> 90
            else -> 100
        }
    }

    private fun calculateTargetTime() {
        val distance = etDistance.text.toString().toIntOrNull() ?: return
        val strokePosition = spinnerStrokeType.selectedItemPosition
        val effortPercent = effortZoneIndexToPercent(seekBarEffort.progress)

        if (distance <= 0 || strokePosition < 0) {
            tvTargetTime.text = "Auto-calculated from PB"
            tvTargetTime.setTextColor(getColor(R.color.text_secondary))
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get current swimmer ID (from intent or default)
                val swimmerId = currentSwimmerId ?: intent.getIntExtra("SWIMMER_ID", -1).takeIf { it > 0 }
                
                if (swimmerId == null || swimmerId <= 0) {
                    withContext(Dispatchers.Main) {
                        tvTargetTime.text = "No swimmer selected"
                        tvTargetTime.setTextColor(getColor(R.color.text_secondary))
                    }
                    return@launch
                }

                // Get swimmer's primary stroke if S1 is selected
                val selectedStroke = strokeTypes[strokePosition]
                val actualStroke = if (selectedStroke == "S1 (Primary)") {
                    val swimmer = db.swimmerDao().getById(swimmerId)
                    swimmer?.primaryStroke ?: "FREESTYLE"
                } else {
                    selectedStroke
                }

                // Convert stroke string to StrokeType enum
                val strokeType = try {
                    com.thesisapp.data.non_dao.StrokeType.valueOf(actualStroke)
                } catch (e: Exception) {
                    com.thesisapp.data.non_dao.StrokeType.FREESTYLE
                }

                // Get PB for this distance and stroke
                val pb = db.personalBestDao().getBySwimmerDistanceStroke(swimmerId, distance, strokeType)

                withContext(Dispatchers.Main) {
                    if (pb != null) {
                        // Spec: target is computed from PB, not the other way around.
                        // Example: 90% effort => PB + (PB * 0.10) => PB * 1.10
                        val addFraction = (1f - (effortPercent / 100f)).coerceAtLeast(0f)
                        val targetTime = pb.bestTime * (1f + addFraction)
                        
                        val minutes = (targetTime / 60).toInt()
                        val seconds = targetTime % 60
                        tvTargetTime.text = String.format("%d:%05.2f", minutes, seconds)
                        tvTargetTime.setTextColor(getColor(R.color.primary))
                    } else {
                        tvTargetTime.text = "No PB for ${distance}m $actualStroke"
                        tvTargetTime.setTextColor(getColor(R.color.text_secondary))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CreateExercise", "Error calculating target time", e)
                withContext(Dispatchers.Main) {
                    tvTargetTime.text = "Error calculating"
                    tvTargetTime.setTextColor(getColor(R.color.error))
                }
            }
        }
    }
}
