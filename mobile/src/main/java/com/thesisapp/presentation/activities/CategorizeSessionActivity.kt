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
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.ExerciseCategory
import com.thesisapp.data.repository.SwimSessionUploadRepository
import com.thesisapp.utils.AuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class CategorizeSessionActivity : AppCompatActivity() {

    @Inject
    lateinit var swimSessionUploadRepository: SwimSessionUploadRepository

    private lateinit var db: AppDatabase
    private lateinit var tvTitle: TextView
    private lateinit var spinnerContext: Spinner
    private lateinit var spinnerExercise: Spinner
    private lateinit var spinnerEnergyZone: Spinner
    private lateinit var spinnerSeasonPhase: Spinner
    private lateinit var inputHrBefore: TextInputEditText
    private lateinit var inputHrAfter: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnSkip: Button

    private var sessionId: Int = -1
    private var contexts = mutableListOf<Pair<String, Int?>>() // (name, teamId or null for personal)
    private var exercises = mutableListOf<Exercise>()

    private var desiredContextTeamId: Int? = null
    private var desiredExerciseId: Int? = null

    private val energyZones = listOf("Select Zone", "REC", "EN1", "EN2", "EN3", "SP1", "SP2", "SP3")
    private val seasonPhases = listOf("Select Phase", "Preparation", "Loading", "Taper", "Competition", "Recovery")

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
        spinnerEnergyZone = findViewById(R.id.spinnerEnergyZone)
        spinnerSeasonPhase = findViewById(R.id.spinnerSeasonPhase)
        inputHrBefore = findViewById(R.id.inputHrBefore)
        inputHrAfter = findViewById(R.id.inputHrAfter)
        btnSave = findViewById(R.id.btnSave)
        btnSkip = findViewById(R.id.btnSkip)

        tvTitle.text = "Categorize Your Session"

        setupSpinners()

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
        prefillData()
    }

    private fun setupSpinners() {
        // Energy Zones
        val ezAdapter = ArrayAdapter(this, R.layout.spinner_item, energyZones)
        ezAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerEnergyZone.adapter = ezAdapter

        // Season Phases
        val spAdapter = ArrayAdapter(this, R.layout.spinner_item, seasonPhases)
        spAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerSeasonPhase.adapter = spAdapter
    }

    private fun prefillData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val session = db.mlResultDao().getBySessionId(sessionId)

            desiredExerciseId = session.exerciseId
            desiredContextTeamId = session.exerciseId?.let { exId ->
                db.exerciseDao().getById(exId)?.teamId
            }

            withContext(Dispatchers.Main) {
                session.let {
                    it.heartRateBefore?.let { hr -> inputHrBefore.setText(hr.toString()) }
                    it.heartRateAfter?.let { hr -> inputHrAfter.setText(hr.toString()) }
                    
                    // Pre-select Energy Zone
                    it.energyZone?.let { zone ->
                        val index = energyZones.indexOf(zone)
                        if (index >= 0) spinnerEnergyZone.setSelection(index)
                    }
                    
                    // Pre-select Season Phase
                    it.seasonPhase?.let { phase ->
                        val index = seasonPhases.indexOf(phase)
                        if (index >= 0) spinnerSeasonPhase.setSelection(index)
                    }
                }
            }
        }
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

                // Prefer the context where the existing exercise belongs (personal vs team)
                val desiredIdx = contexts.indexOfFirst { it.second == desiredContextTeamId }
                    .takeIf { it >= 0 }
                    ?: 0

                spinnerContext.setSelection(desiredIdx)
                loadExercisesForContext(desiredIdx)
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

                desiredExerciseId?.let { exId ->
                    val idx = exercises.indexOfFirst { it.id == exId }
                    if (idx >= 0) {
                        spinnerExercise.setSelection(idx)
                    }
                }
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

        val effortLabel = selectedExercise.effortLevel?.let { percent ->
            when {
                percent <= 40 -> "Easy"
                percent <= 70 -> "Moderate"
                percent <= 90 -> "Hard"
                else -> "Max Effort"
            }
        }

        // Get manual HR values
        val hrBeforeManual = inputHrBefore.text.toString().toIntOrNull()
        val hrAfterManual = inputHrAfter.text.toString().toIntOrNull()

        // Get Energy Zone and Season Phase
        val selectedEnergyZone = if (spinnerEnergyZone.selectedItemPosition > 0) energyZones[spinnerEnergyZone.selectedItemPosition] else null
        val selectedSeasonPhase = if (spinnerSeasonPhase.selectedItemPosition > 0) seasonPhases[spinnerSeasonPhase.selectedItemPosition] else null

        lifecycleScope.launch(Dispatchers.IO) {
            // Update the session with categorization
            val session = db.mlResultDao().getBySessionId(sessionId)
            if (session != null) {
                val resolvedExerciseId = selectedExercise.id.takeIf { it > 0 }
                val updated = session.copy(
                    exerciseId = resolvedExerciseId,
                    exerciseName = selectedExercise.name,
                    distance = selectedExercise.distance,
                    sets = selectedExercise.sets,
                    reps = 1,
                    effortLevel = effortLabel,
                    heartRateBefore = hrBeforeManual,
                    heartRateAfter = hrAfterManual,
                    energyZone = selectedEnergyZone,
                    seasonPhase = selectedSeasonPhase
                )
                db.mlResultDao().update(updated)

                try {
                    swimSessionUploadRepository.uploadSession(
                        sessionId = sessionId,
                        includeSamples = false
                    )
                } catch (e: Exception) {
                    android.util.Log.d("DEBUG", "Supabase uploadSession (categorization) failed", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@CategorizeSessionActivity,
                            e.message ?: "Failed to upload session update to Supabase",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CategorizeSessionActivity, "Session categorized!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
