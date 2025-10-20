package com.thesisapp.presentation.exercises.energy

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.thesisapp.R
import com.thesisapp.data.EnergySystemRepository
import com.thesisapp.domain.EnergySystem

class EnergySystemsActivity : AppCompatActivity() {
    private lateinit var adapter: EnergySystemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_energy_systems)

        adapter = EnergySystemAdapter(
            onEdit = { openEdit(it) },
            onDelete = { EnergySystemRepository.delete(it.id) }
        )

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        EnergySystemRepository.getEnergySystems().observe(this) { list ->
            adapter.submit(list)
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(Intent(this, AddEditEnergySystemActivity::class.java))
        }

        findViewById<android.widget.ImageButton>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun openEdit(item: EnergySystem) {
        val i = Intent(this, AddEditEnergySystemActivity::class.java)
        i.putExtra(AddEditEnergySystemActivity.EXTRA_ID, item.id)
        startActivity(i)
    }
}
