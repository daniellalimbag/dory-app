package com.thesisapp.presentation.exercises.energy

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.thesisapp.R
import com.thesisapp.data.EnergySystemRepository
import com.thesisapp.domain.EnergySystem

class AddEditEnergySystemActivity : AppCompatActivity() {
    companion object { const val EXTRA_ID = "extra_id" }

    private var editingId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_energy_system)

        val etExample: EditText = findViewById(R.id.etExample)
        val etName: EditText = findViewById(R.id.etNamePurpose)
        val etCode: EditText = findViewById(R.id.etCode)
        val etEffort: EditText = findViewById(R.id.etEffort)
        val etTypical: EditText = findViewById(R.id.etTypical)
        val etRep: EditText = findViewById(R.id.etRep)
        val etRest: EditText = findViewById(R.id.etRest)
        val etDuration: EditText = findViewById(R.id.etDuration)
        val btnSave: Button = findViewById(R.id.btnSave)
        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id != -1L) {
            editingId = id
            // For in-memory repo we can't fetch by id directly; reconstruct from list
            EnergySystemRepository.getEnergySystems().value?.firstOrNull { it.id == id }?.let { es ->
                etExample.setText(es.exampleWorkout)
                etName.setText(es.namePurpose)
                etCode.setText(es.code)
                etEffort.setText(es.effort)
                etTypical.setText(es.typicalDistancePerSet)
                etRep.setText(es.repDistance)
                etRest.setText(es.restInterval)
                etDuration.setText(es.totalDurationSet)
            }
        }

        btnSave.setOnClickListener {
            val item = EnergySystem(
                id = editingId ?: System.currentTimeMillis(),
                exampleWorkout = etExample.text.toString().trim(),
                namePurpose = etName.text.toString().trim(),
                code = etCode.text.toString().trim(),
                effort = etEffort.text.toString().trim(),
                typicalDistancePerSet = etTypical.text.toString().trim(),
                repDistance = etRep.text.toString().trim(),
                restInterval = etRest.text.toString().trim(),
                totalDurationSet = etDuration.text.toString().trim()
            )
            if (editingId == null) EnergySystemRepository.add(item) else EnergySystemRepository.update(item)
            finish()
        }
    }
}
