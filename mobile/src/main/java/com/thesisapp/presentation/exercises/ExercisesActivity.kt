package com.thesisapp.presentation.exercises

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.thesisapp.R
import com.thesisapp.data.ExerciseRepository
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole

class ExercisesActivity : AppCompatActivity() {
    private lateinit var adapter: ExerciseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercises)

        adapter = ExerciseAdapter()
        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            startActivity(Intent(this, AddExerciseActivity::class.java))
        }

        // Hide add button for swimmers (view-only)
        val role = AuthManager.currentUser(this)?.role
        if (role == UserRole.SWIMMER) {
            fab.hide()
        }

        ExerciseRepository.getExercises().observe(this, Observer { list ->
            adapter.submitList(list)
        })

        // Support optional header back button if present in the layout
        findViewById<android.widget.ImageButton?>(R.id.btnBack)?.setOnClickListener { finish() }
    }
}
