package com.thesisapp.presentation

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.MlResult
import com.thesisapp.presentation.adapters.SessionAdapter
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UncategorizedSessionsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var sessionsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private var sessions = listOf<MlResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_uncategorized_sessions)

        db = AppDatabase.getInstance(this)

        sessionsRecyclerView = findViewById(R.id.sessionsRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)

        sessionsRecyclerView.layoutManager = LinearLayoutManager(this)

        supportActionBar?.title = "Uncategorized Sessions"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val swimmerId = intent.getIntExtra("SWIMMER_ID", -1)
            
            android.util.Log.d("UncategorizedSessions", "Loading for swimmerId: $swimmerId")
            
            sessions = if (swimmerId != -1) {
                val allSessions = db.mlResultDao().getResultsForSwimmer(swimmerId)
                android.util.Log.d("UncategorizedSessions", "Total sessions: ${allSessions.size}")
                val uncategorized = allSessions.filter { session -> session.exerciseId == null }
                android.util.Log.d("UncategorizedSessions", "Uncategorized sessions: ${uncategorized.size}")
                uncategorized
            } else {
                android.util.Log.d("UncategorizedSessions", "Invalid swimmerId")
                emptyList()
            }

            withContext(Dispatchers.Main) {
                if (sessions.isEmpty()) {
                    android.util.Log.d("UncategorizedSessions", "Showing empty state")
                    sessionsRecyclerView.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                } else {
                    android.util.Log.d("UncategorizedSessions", "Showing ${sessions.size} sessions")
                    sessionsRecyclerView.visibility = View.VISIBLE
                    emptyStateLayout.visibility = View.GONE
                    
                    val adapter = SessionAdapter(sessions) { session ->
                        // Open categorization activity
                        val intent = android.content.Intent(this@UncategorizedSessionsActivity, CategorizeSessionActivity::class.java)
                        intent.putExtra("sessionId", session.sessionId)
                        startActivity(intent)
                    }
                    sessionsRecyclerView.adapter = adapter
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
