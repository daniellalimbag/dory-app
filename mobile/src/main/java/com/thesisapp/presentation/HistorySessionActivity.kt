package com.thesisapp.presentation

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.MlResult
import com.thesisapp.data.SwimData
import com.thesisapp.utils.StrokeMetrics
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistorySessionActivity : AppCompatActivity() {

    private lateinit var btnReturn: ImageButton
    private lateinit var txtDate: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtStart: TextView
    private lateinit var txtEnd: TextView
    private lateinit var txtSwimmingVelocity: TextView
    private lateinit var txtStrokeRate: TextView
    private lateinit var txtStrokeLength: TextView
    private lateinit var txtStrokeIndex: TextView
    private lateinit var inputNotes: EditText
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history_session)

        // Initialize all views and the database instance
        initializeViews()

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }

        // 1. Get Session data from the Intent
        val sessionDate = intent.getStringExtra("EXTRA_SESSION_DATE")
        val sessionTime = intent.getStringExtra("EXTRA_SESSION_TIME")
        val sessionFileName = intent.getStringExtra("EXTRA_SESSION_FILENAME")

        if (sessionDate == null || sessionTime == null || sessionFileName == null) {
            Toast.makeText(this, "Error: Incomplete session data", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 2. Immediately display the data from the Session object
        displaySessionInfo(sessionDate, sessionTime)

        // 3. Compute kinematic metrics from stored sensor data
        val sessionId = sessionFileName.removeSuffix(".csv").split("_").lastOrNull()?.toIntOrNull()
        if (sessionId != null) {
            computeAndDisplayKinematicMetrics(sessionId)
        }

        // 4. Load the MlResult from the database specifically for dynamic data (notes)
        if (sessionId != null) {
            loadDynamicData(sessionId)
        } else {
            Toast.makeText(this, "Error: Invalid session ID format", Toast.LENGTH_LONG).show()
        }
    }

    private fun initializeViews() {
        btnReturn = findViewById(R.id.btnReturn)
        txtDate = findViewById(R.id.txtDate)
        txtDuration = findViewById(R.id.txtDuration)
        txtStart = findViewById(R.id.txtStart)
        txtEnd = findViewById(R.id.txtEnd)
        txtSwimmingVelocity = findViewById(R.id.txtSwimmingVelocity)
        txtStrokeRate = findViewById(R.id.txtStrokeRate)
        txtStrokeLength = findViewById(R.id.txtStrokeLength)
        txtStrokeIndex = findViewById(R.id.txtStrokeIndex)
        inputNotes = findViewById(R.id.inputNotes)
        db = AppDatabase.getInstance(this)
    }

    private fun displaySessionInfo(date: String, time: String) {
        txtDate.text = date
        val timeParts = time.split(" - ")
        if (timeParts.size == 2) {
            val startTime = timeParts[0]
            val endTime = timeParts[1]
            txtStart.text = startTime
            txtEnd.text = endTime
            txtDuration.text = calculateDuration(startTime, endTime)
        } else {
            txtStart.text = time
            txtEnd.text = "-"
            txtDuration.text = "-" // Set a default duration if format is unexpected
        }
    }

    private fun computeAndDisplayKinematicMetrics(sessionId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val swimData: List<SwimData> = db.swimDataDao().getSwimDataForSession(sessionId)
                val strokeCount = StrokeMetrics.computeStrokeCount(swimData).toDouble()

                // Fixed parameters
                val poolLengthMeters = 50.0
                val lapTimeSeconds = 35.0

                val swimmingVelocity = poolLengthMeters / lapTimeSeconds // m/s
                val strokeRate = if (lapTimeSeconds > 0) strokeCount / lapTimeSeconds else 0.0 // strokes/sec
                val strokeLength = if (strokeCount > 0) poolLengthMeters / strokeCount else 0.0 // m/stroke
                val strokeIndex = swimmingVelocity * strokeLength

                withContext(Dispatchers.Main) {
                    txtSwimmingVelocity.text = String.format("%.2f m/s", swimmingVelocity)
                    txtStrokeRate.text = String.format("%.2f strokes/sec", strokeRate)
                    txtStrokeLength.text = String.format("%.2f m/stroke", strokeLength)
                    txtStrokeIndex.text = String.format("%.2f", strokeIndex)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HistorySessionActivity, "Failed to compute metrics: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadDynamicData(sessionId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = db.mlResultDao().getBySessionId(sessionId)
            result?.let { mlResult ->
                withContext(Dispatchers.Main) {
                    // --- MODIFICATION: Only update the notes field ---
                    inputNotes.setText(mlResult.notes)

                    // Set up the listener to save notes when focus is lost
                    inputNotes.setOnFocusChangeListener { _, hasFocus ->
                        if (!hasFocus) {
                            val newNotes = inputNotes.text.toString()
                            if (newNotes != mlResult.notes) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    db.mlResultDao().update(mlResult.copy(notes = newNotes))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun calculateDuration(start: String, end: String): String {
        // This function now correctly calculates duration based on the provided start and end times
        return try {
            val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val startTime = format.parse(start)!!
            val endTime = format.parse(end)!!
            val diff = endTime.time - startTime.time

            val minutes = (diff / 60000).toInt()
            val seconds = (diff % 60000 / 1000).toInt()
            String.format("%02d:%02d", minutes, seconds)
        } catch (e: Exception) {
            "-"
        }
    }
}