package com.thesisapp.presentation.exercises

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.thesisapp.R
import com.thesisapp.data.ExerciseRepository
import com.thesisapp.data.EnergySystemRepository
import com.thesisapp.domain.Exercise
import com.thesisapp.domain.ExerciseComponent

class AddExerciseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_exercise)

        // Top section: Energy System selection and details
        val spEnergyTop: Spinner = findViewById(R.id.spEnergyTop)
        val etESCode: EditText = findViewById(R.id.etESCode)
        val etESName: EditText = findViewById(R.id.etESName)
        val etESEffort: EditText = findViewById(R.id.etESEffort)
        val etESTypical: EditText = findViewById(R.id.etESTypical)
        val etESRep: EditText = findViewById(R.id.etESRep)
        val etESRest: EditText = findViewById(R.id.etESRest)
        val etESDuration: EditText = findViewById(R.id.etESDuration)
        val etESExample: EditText = findViewById(R.id.etESExample)
        val btnNewES: Button = findViewById(R.id.btnNewES)
        val btnDeleteES: Button = findViewById(R.id.btnDeleteES)
        val btnSaveES: Button = findViewById(R.id.btnSaveES)
        val btnNext: Button = findViewById(R.id.btnNextToExercise)

        // Bottom section (initially hidden)
        val sectionExercise: android.view.View = findViewById(R.id.sectionExercise)
        val etDay: EditText = findViewById(R.id.etDay)
        val etFocus: EditText = findViewById(R.id.etFocus)
        val btnSave: Button = findViewById(R.id.btnSave)

        // Components UI
        val spEnergy: Spinner = findViewById(R.id.spEnergy)
        val etCompDesc: EditText = findViewById(R.id.etCompDesc)
        val etCompDistance: EditText = findViewById(R.id.etCompDistance)
        val btnAddComponent: Button = findViewById(R.id.btnAddComponent)
        val rvComponents: androidx.recyclerview.widget.RecyclerView = findViewById(R.id.rvComponents)
        val tvTotal: TextView = findViewById(R.id.tvTotal)

        val compAdapter = ExerciseComponentAdapter()
        rvComponents.layoutManager = LinearLayoutManager(this)
        rvComponents.adapter = compAdapter

        // State for current ES id (to support rename of code)
        var currentId: Long? = null

        fun refreshSpinner(selectCode: String? = null) {
            val list = EnergySystemRepository.getEnergySystems().value ?: emptyList()
            val codes = list.map { it.code }
            spEnergyTop.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)
            val idx = selectCode?.let { codes.indexOf(it) } ?: if (codes.isNotEmpty()) 0 else -1
            if (idx >= 0) spEnergyTop.setSelection(idx)
        }

        fun fillEnergyDetailsByCode(code: String?) {
            val list = EnergySystemRepository.getEnergySystems().value ?: emptyList()
            val es = list.firstOrNull { it.code.equals(code, true) }
            if (es != null) {
                currentId = es.id
                etESCode.setText(es.code)
                etESName.setText(es.namePurpose)
                etESEffort.setText(es.effort)
                etESTypical.setText(es.typicalDistancePerSet)
                etESRep.setText(es.repDistance)
                etESRest.setText(es.restInterval)
                etESDuration.setText(es.totalDurationSet)
                etESExample.setText(es.exampleWorkout)
            } else {
                currentId = null
                etESCode.setText("")
                etESName.setText("")
                etESEffort.setText("")
                etESTypical.setText("")
                etESRep.setText("")
                etESRest.setText("")
                etESDuration.setText("")
                etESExample.setText("")
            }
        }

        // Observe ES list to keep spinner in sync
        EnergySystemRepository.getEnergySystems().observe(this) { _ ->
            val selected = spEnergyTop.selectedItem?.toString()
            refreshSpinner(selected)
            // Also keep component code spinner in sync with all codes
            refreshComponentCodes(spEnergy, spEnergy.selectedItem?.toString())
        }

        // Initialize
        refreshSpinner()
        fillEnergyDetailsByCode(spEnergyTop.selectedItem?.toString())

        spEnergyTop.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                val code = parent.getItemAtPosition(position)?.toString()
                fillEnergyDetailsByCode(code)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // New, Save, Delete handlers
        btnNewES.setOnClickListener {
            currentId = null
            etESCode.setText("")
            etESName.setText("")
            etESEffort.setText("")
            etESTypical.setText("")
            etESRep.setText("")
            etESRest.setText("")
            etESDuration.setText("")
            etESExample.setText("")
        }

        btnSaveES.setOnClickListener {
            val item = com.thesisapp.domain.EnergySystem(
                id = currentId ?: System.currentTimeMillis(),
                exampleWorkout = etESExample.text.toString().trim(),
                namePurpose = etESName.text.toString().trim(),
                code = etESCode.text.toString().trim(),
                effort = etESEffort.text.toString().trim(),
                typicalDistancePerSet = etESTypical.text.toString().trim(),
                repDistance = etESRep.text.toString().trim(),
                restInterval = etESRest.text.toString().trim(),
                totalDurationSet = etESDuration.text.toString().trim()
            )
            if (currentId == null) {
                EnergySystemRepository.add(item)
            } else {
                EnergySystemRepository.update(item)
            }
            currentId = item.id
            refreshSpinner(item.code)
            fillEnergyDetailsByCode(item.code)
        }

        btnDeleteES.setOnClickListener {
            currentId?.let {
                EnergySystemRepository.delete(it)
                currentId = null
                refreshSpinner()
                fillEnergyDetailsByCode(spEnergyTop.selectedItem?.toString())
            }
        }

        fun updateTotal() {
            val total = compAdapter.items().sumOf { it.distance }
            tvTotal.text = "Total: ${total} m"
        }

        // Next shows exercise inputs and loads ALL energy system codes for components
        btnNext.setOnClickListener {
            refreshComponentCodes(spEnergy, spEnergyTop.selectedItem?.toString())
            sectionExercise.visibility = android.view.View.VISIBLE
        }

        btnAddComponent.setOnClickListener {
            val code = spEnergy.selectedItem?.toString()?.ifBlank { null }
            val dist = etCompDistance.text.toString().toIntOrNull()
            if (code != null && dist != null && dist > 0) {
                val comp = ExerciseComponent(
                    energyCode = code,
                    description = etCompDesc.text.toString().ifBlank { null },
                    distance = dist
                )
                compAdapter.addItem(comp)
                etCompDesc.setText("")
                etCompDistance.setText("")
                updateTotal()
            }
        }

        btnSave.setOnClickListener {
            val components = compAdapter.items()
            val totalFromComponents = components.sumOf { it.distance }
            val selectedCode = spEnergyTop.selectedItem?.toString()?.ifBlank { null }
            val exercise = Exercise(
                title = selectedCode ?: "Exercise",
                distance = null,
                interval = null,
                time = null,
                strokeCount = null,
                preHr = null,
                postHr = null,
                notes = null,
                day = etDay.text.toString().trim().ifBlank { null },
                focus = etFocus.text.toString().trim().ifBlank { null },
                components = components,
                total = if (totalFromComponents > 0) totalFromComponents else null
            )
            ExerciseRepository.add(exercise)
            finish()
        }
    }

    private fun refreshComponentCodes(spinner: Spinner, selectCode: String? = null) {
        val list = EnergySystemRepository.getEnergySystems().value ?: emptyList()
        val codes = list.map { it.code }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, codes)
        val idx = selectCode?.let { codes.indexOf(it) } ?: -1
        if (idx >= 0) spinner.setSelection(idx)
    }
}
