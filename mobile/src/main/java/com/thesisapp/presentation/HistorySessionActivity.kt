package com.thesisapp.presentation

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistorySessionActivity : AppCompatActivity() {

    private lateinit var btnReturn: ImageButton
    private lateinit var txtDate: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtStart: TextView
    private lateinit var txtEnd: TextView
    private lateinit var txtStrokeBack: TextView
    private lateinit var txtStrokeBreast: TextView
    private lateinit var txtStrokeFly: TextView
    private lateinit var txtStrokeFree: TextView
    private lateinit var inputNotes: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.history_session)

        btnReturn = findViewById(R.id.btnReturn)
        txtDate = findViewById(R.id.txtDate)
        txtDuration = findViewById(R.id.txtDuration)
        txtStart = findViewById(R.id.txtStart)
        txtEnd = findViewById(R.id.txtEnd)
        txtStrokeBack = findViewById(R.id.txtStrokeBack)
        txtStrokeBreast = findViewById(R.id.txtStrokeBreast)
        txtStrokeFly = findViewById(R.id.txtStrokeFly)
        txtStrokeFree = findViewById(R.id.txtStrokeFree)
        inputNotes = findViewById(R.id.inputNotes)

        btnReturn.setOnClickListener {
            it.animateClick()
            finish()
        }

        val sessionId = intent.getIntExtra("sessionId", -1)
        if (sessionId != -1) {
            loadSession(sessionId)
        }
    }

    private fun loadSession(sessionId: Int) {
        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            val mlResult = withContext(Dispatchers.IO) { db.mlResultDao().getBySessionId(sessionId) }
            mlResult?.let {
                txtDate.text = it.date
                txtStart.text = it.timeStart
                txtEnd.text = it.timeEnd
                txtDuration.text = calculateDuration(it.timeStart, it.timeEnd)
                txtStrokeBack.text = "${it.backstroke}%"
                txtStrokeBreast.text = "${it.breaststroke}%"
                txtStrokeFly.text = "${it.butterfly}%"
                txtStrokeFree.text = "${it.freestyle}%"
                inputNotes.setText(it.notes)
                inputNotes.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val newNotes = inputNotes.text.toString()
                        if (newNotes != it.notes) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                db.mlResultDao().update(it.copy(notes = newNotes))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun calculateDuration(start: String, end: String): String {
        try {
            val format = java.text.SimpleDateFormat("HH:mm:ss")
            val startTime = format.parse(start)
            val endTime = format.parse(end)
            val diff = endTime.time - startTime.time

            val minutes = (diff / 60000).toInt()
            val seconds = (diff % 60000 / 1000).toInt()
            return String.format("%02d:%02d", minutes, seconds)
        } catch (e: Exception) {
            return "-"
        }
    }
}
