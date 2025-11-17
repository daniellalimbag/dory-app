package com.thesisapp.presentation

import android.app.Activity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.Exercise
import com.thesisapp.data.Swimmer
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportAssignActivity : AppCompatActivity() {

    private lateinit var spinnerSwimmer: Spinner
    private lateinit var spinnerExercise: Spinner
    private lateinit var txtValidation: TextView
    private lateinit var btnCancel: Button
    private lateinit var btnAssign: Button

    private var swimmers: List<Swimmer> = emptyList()
    private var exercises: List<Exercise> = emptyList()
    private var preselectedSwimmerId: Int = -1

    companion object {
        const val EXTRA_SWIMMER_ID = "EXTRA_SWIMMER_ID"
        const val EXTRA_EXERCISE_ID = "EXTRA_EXERCISE_ID"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import_assign)

        spinnerSwimmer = findViewById(R.id.spinnerSwimmer)
        spinnerExercise = findViewById(R.id.spinnerExercise)
        txtValidation = findViewById(R.id.txtValidation)
        btnCancel = findViewById(R.id.btnCancel)
        btnAssign = findViewById(R.id.btnAssign)

        txtValidation.text = ""

        preselectedSwimmerId = intent.getIntExtra(EXTRA_SWIMMER_ID, -1)

        loadData()

        btnCancel.setOnClickListener {
            it.animateClick()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        btnAssign.setOnClickListener {
            it.animateClick()
            validateSelection()
        }
    }

    private fun loadData() {
        val teamId = AuthManager.currentTeamId(this)
        if (teamId == null) {
            Toast.makeText(this, "No active team found", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val db = AppDatabase.getInstance(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val swimmersDb = db.teamMembershipDao().getSwimmersForTeam(teamId)
            val exercisesDb = db.exerciseDao().getExercisesForTeam(teamId)

            withContext(Dispatchers.Main) {
                swimmers = swimmersDb
                exercises = exercisesDb

                val swimmerNames = listOf("Select swimmer") + swimmers.map { it.name }
                val exerciseNames = listOf("Select exercise") + exercises.map { it.name }

                spinnerSwimmer.adapter = ArrayAdapter(
                    this@ImportAssignActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    swimmerNames
                )

                if (preselectedSwimmerId > 0) {
                    val index = swimmers.indexOfFirst { it.id == preselectedSwimmerId }
                    if (index >= 0) {
                        spinnerSwimmer.setSelection(index + 1)
                        spinnerSwimmer.isEnabled = false
                    }
                }

                spinnerExercise.adapter = ArrayAdapter(
                    this@ImportAssignActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    exerciseNames
                )
            }
        }
    }

    private fun validateSelection() {
        val swimmerIndex = spinnerSwimmer.selectedItemPosition
        val exerciseIndex = spinnerExercise.selectedItemPosition

        val swimmerValid = swimmerIndex > 0 && swimmers.isNotEmpty()
        val exerciseValid = exerciseIndex > 0 && exercises.isNotEmpty()

        if (!swimmerValid || !exerciseValid) {
            txtValidation.text = "Please select both a swimmer and an exercise."
            return
        }

        txtValidation.text = ""

        val selectedSwimmer = swimmers[swimmerIndex - 1]
        val selectedExercise = exercises[exerciseIndex - 1]

        val data = intent.apply {
            putExtra(EXTRA_SWIMMER_ID, selectedSwimmer.id)
            putExtra(EXTRA_EXERCISE_ID, selectedExercise.id)
        }

        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
