package com.thesisapp.presentation.swimmer

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.thesisapp.R

class CoachesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coaches)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        val adapter = CoachesAdapter { coach ->
            val i = Intent(this, CoachDetailActivity::class.java)
            i.putExtra(CoachDetailActivity.EXTRA_NAME, coach.name)
            i.putExtra(CoachDetailActivity.EXTRA_EMAIL, coach.email)
            startActivity(i)
        }
        rv.adapter = adapter

        // Dummy coaches
        val data = listOf(
            Coach("Coach Ixxi", "coach.ixxi@team.com"),
            Coach("Coach Evan", "coach.evan@team.com")
        )
        adapter.submit(data)
    }
}

data class Coach(val name: String, val email: String)
