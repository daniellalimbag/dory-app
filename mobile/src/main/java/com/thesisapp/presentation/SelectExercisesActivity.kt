package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.thesisapp.R
import com.thesisapp.data.ExerciseRepository
import com.thesisapp.domain.Exercise

class SelectExercisesActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SWIMMER_ID = "EXTRA_SWIMMER_ID"
        const val EXTRA_EXERCISE_TITLES = "EXTRA_EXERCISE_TITLES"
    }

    private lateinit var adapter: ExerciseSelectAdapter
    private var swimmerId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_exercises)

        swimmerId = intent.getIntExtra(EXTRA_SWIMMER_ID, -1)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ExerciseSelectAdapter()
        rv.adapter = adapter

        // Load exercises
        ExerciseRepository.getExercises().observe(this) { list ->
            adapter.submitList(list)
        }

        findViewById<Button>(R.id.btnStartRecording).setOnClickListener {
            val selected: List<Exercise> = adapter.getSelected()
            val titles = ArrayList(selected.map { it.title })
            val i = Intent(this, TrackSwimmerActivity::class.java)
            i.putExtra("SWIMMER_ID", swimmerId)
            i.putStringArrayListExtra(EXTRA_EXERCISE_TITLES, titles)
            startActivity(i)
            finish()
        }
    }
}
