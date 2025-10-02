package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.animateClick

class HistoryListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var btnReturn: ImageButton
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history_list)

        btnReturn = findViewById(R.id.btnReturn)
        recyclerView = findViewById(R.id.recyclerViewSessions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        db = AppDatabase.getInstance(this)

        loadSessionSummaries()

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }
    }

    private fun loadSessionSummaries() {
        Thread {
            // getSessionSummaries() returns List<MlResult> with unique sessions
            val sessions = db.mlResultDao().getSessionSummaries()

            runOnUiThread {
                recyclerView.adapter = HistoryListAdapter(sessions) { session ->
                    val intent = Intent(this, HistorySessionActivity::class.java).apply {
                        putExtra("sessionId", session.sessionId)
                    }
                    startActivity(intent)
                }
            }
        }.start()
    }
}