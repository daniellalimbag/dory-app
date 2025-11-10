package com.thesisapp.presentation.swimmer

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.thesisapp.R

class CoachDetailActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_EMAIL = "extra_email"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coach_detail)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val name = intent.getStringExtra(EXTRA_NAME) ?: "Coach"
        val email = intent.getStringExtra(EXTRA_EMAIL) ?: ""

        findViewById<TextView>(R.id.tvCoachName).text = name
        findViewById<TextView>(R.id.tvCoachEmail).text = email

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = com.thesisapp.presentation.exercises.ExerciseAdapter()
        rv.adapter = adapter
    }
}

