package com.thesisapp.presentation.activities

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.communication.PhoneReceiver
import com.thesisapp.communication.PhoneSender
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.ExerciseCategory
import com.thesisapp.data.repository.SwimSessionUploadRepository
import com.thesisapp.utils.AuthManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class CategorizeSessionActivity : AppCompatActivity() {

    @Inject
    lateinit var swimSessionUploadRepository: SwimSessionUploadRepository

    @Inject
    lateinit var swimSessionsRepository: com.thesisapp.data.repository.SwimSessionsRepository

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

    private lateinit var btnSyncWatch: Button
    private lateinit var progressSync: ProgressBar
    private lateinit var tvSyncStatus: TextView
    private lateinit var phoneSender: PhoneSender
    private lateinit var phoneReceiver: PhoneReceiver

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

        // Initialize communication
        phoneSender = PhoneSender(this)
        phoneReceiver = PhoneReceiver(this, MutableStateFlow(null))

        phoneReceiver.syncListener = object : PhoneReceiver.SyncListener {
            override fun onSyncTransferStarted() {
                runOnUiThread {
                    tvSyncStatus.text = "Watch connected. Transferring data..."
                    progressSync.isIndeterminate = true
                }
            }

            override fun onSyncCompleted(count: Int) {
                runOnUiThread {
                    tvSyncStatus.text = "Sync Complete! $count points recovered."
                    tvSyncStatus.setTextColor(getColor(R.color.primary))
                    progressSync.visibility = View.GONE
                    btnSyncWatch.visibility = View.GONE // Hide button on success
                    prefillData() // Refresh UI
                }
            }

            override fun onSyncFailed(error: String) {
                runOnUiThread {
                    tvSyncStatus.text = "Sync Failed: $error"
                    tvSyncStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                    progressSync.visibility = View.GONE
                    btnSyncWatch.isEnabled = true
                }
            }
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

        btnSyncWatch = findViewById(R.id.btnSyncWatch)
        progressSync = findViewById(R.id.progressSync)
        tvSyncStatus = findViewById(R.id.tvSyncStatus)

        tvTitle.text = "Categorize Your Session"

        setupSpinners()

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

        btnSyncWatch.setOnClickListener {
            startSync()
        }

        loadContexts()
        prefillData()
    }

    override fun onStart() {
        super.onStart()
        phoneReceiver.register()
    }

    override fun onStop() {
        super.onStop()
        phoneReceiver.unregister()
    }

    private fun startSync() {
        btnSyncWatch.isEnabled = false
        progressSync.visibility = View.VISIBLE
        tvSyncStatus.text = "Requesting data from watch..."
        tvSyncStatus.setTextColor(getColor(R.color.text))

        phoneSender.requestSync(
            sessionId = sessionId,
            onSuccess = {
                runOnUiThread {
                    tvSyncStatus.text = "Waiting for watch to prepare data..."
                }
            },
            onFailure = {
                runOnUiThread {
                    btnSyncWatch.isEnabled = true
                    progressSync.visibility = View.GONE
                    tvSyncStatus.text = "Watch not reachable. Ensure Dory is open on watch."
                    tvSyncStatus.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        )
    }

    private fun setupSpinners() {
        val ezAdapter = ArrayAdapter(this, R.layout.spinner_item, energyZones)
        ezAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerEnergyZone.adapter = ezAdapter

        val spAdapter = ArrayAdapter(this, R.layout.spinner_item, seasonPhases)
        spAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerSeasonPhase.adapter = spAdapter
    }

    private fun prefillData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val session = runCatching {
                val swimmerId = intent.getIntExtra("SWIMMER_ID", -1)
                val sessions = swimSessionsRepository.getSessionsForSwimmer(swimmerId.toLong())
                val found = sessions.find { it.sessionId == sessionId }

                if (found != null) {
                    val existing = db.mlResultDao().getBySessionId(sessionId)
                    if (existing == null) {
                        db.mlResultDao().insert(found)
                    } else {
                        db.mlResultDao().update(found)
                    }
                }
                found
            }.getOrElse { error ->
                db.mlResultDao().getBySessionId(sessionId)
            }

            if (session == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CategorizeSessionActivity, "Session not found (ID: $sessionId)", Toast.LENGTH_LONG).show()
                    finish()
                }
                return@launch
            }

            desiredExerciseId = session.exerciseId
            desiredContextTeamId = session.exerciseId?.let { exId ->
                db.exerciseDao().getById(exId)?.teamId
            }

            withContext(Dispatchers.Main) {
                session.heartRateBefore?.let { hr -> inputHrBefore.setText(hr.toString()) }
                session.heartRateAfter?.let { hr -> inputHrAfter.setText(hr.toString()) }
                
                session.energyZone?.let { zone ->
                    val index = energyZones.indexOf(zone)
                    if (index >= 0) spinnerEnergyZone.setSelection(index)
                }
                
                session.seasonPhase?.let { phase ->
                    val index = seasonPhases.indexOf(phase)
                    if (index >= 0) spinnerSeasonPhase.setSelection(index)
                }
            }
        }
    }

    private fun loadContexts() {
        lifecycleScope.launch(Dispatchers.IO) {
            contexts.clear()
            contexts.add(Pair("Personal", null))
            
            val teamId = AuthManager.currentTeamId(this@CategorizeSessionActivity)
            if (teamId != null) {
                val team = db.teamDao().getById(teamId)
                team?.let { contexts.add(Pair(it.name, it.id)) }
            }

            withContext(Dispatchers.Main) {
                val contextNames = contexts.map { it.first }
                val adapter = ArrayAdapter(this@CategorizeSessionActivity, R.layout.spinner_item, contextNames)
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                spinnerContext.adapter = adapter

                val desiredIdx = contexts.indexOfFirst { it.second == desiredContextTeamId }.takeIf { it >= 0 } ?: 0
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
            val swimmerId = intent.getIntExtra("SWIMMER_ID", -1)
            val swimmer = if (swimmerId != -1) db.swimmerDao().getById(swimmerId) else null
            val swimmerCategory = swimmer?.category ?: ExerciseCategory.SPRINT
            
            val allExercises = if (selectedContext.second == null) {
                db.exerciseDao().getExercisesForTeam(-1)
            } else {
                db.exerciseDao().getExercisesForTeam(selectedContext.second!!)
            }
            
            exercises.addAll(allExercises.filter { it.category == swimmerCategory })
            exercises.add(0, Exercise(
                id = -1, teamId = -1, name = "General Training", category = swimmerCategory,
                description = "General swim training", sets = 1, distance = 0, effortLevel = 50
            ))

            withContext(Dispatchers.Main) {
                val exerciseNames = exercises.map { it.name }
                val adapter = ArrayAdapter(this@CategorizeSessionActivity, R.layout.spinner_item, exerciseNames)
                adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
                spinnerExercise.adapter = adapter

                desiredExerciseId?.let { exId ->
                    val idx = exercises.indexOfFirst { it.id == exId }
                    if (idx >= 0) spinnerExercise.setSelection(idx)
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

        val hrBeforeManual = inputHrBefore.text.toString().toIntOrNull()
        val hrAfterManual = inputHrAfter.text.toString().toIntOrNull()
        val selectedEnergyZone = if (spinnerEnergyZone.selectedItemPosition > 0) energyZones[spinnerEnergyZone.selectedItemPosition] else null
        val selectedSeasonPhase = if (spinnerSeasonPhase.selectedItemPosition > 0) seasonPhases[spinnerSeasonPhase.selectedItemPosition] else null

        lifecycleScope.launch(Dispatchers.IO) {
            val session = db.mlResultDao().getBySessionId(sessionId)
            if (session != null) {
                val updated = session.copy(
                    exerciseId = selectedExercise.id.takeIf { it > 0 },
                    exerciseName = selectedExercise.name,
                    distance = selectedExercise.distance,
                    sets = selectedExercise.sets,
                    effortLevel = effortLabel,
                    heartRateBefore = hrBeforeManual,
                    heartRateAfter = hrAfterManual,
                    energyZone = selectedEnergyZone,
                    seasonPhase = selectedSeasonPhase
                )
                db.mlResultDao().update(updated)

                try {
                    swimSessionUploadRepository.uploadSession(sessionId = sessionId, includeSamples = false)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CategorizeSessionActivity, e.message ?: "Upload failed", Toast.LENGTH_LONG).show()
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