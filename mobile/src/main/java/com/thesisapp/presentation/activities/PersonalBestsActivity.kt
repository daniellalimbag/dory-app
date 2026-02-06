package com.thesisapp.presentation.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.PersonalBest
import com.thesisapp.data.non_dao.StrokeType
import com.thesisapp.utils.animateClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class PersonalBestsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var rvPersonalBests: RecyclerView
    private lateinit var emptyState: View
    private lateinit var adapter: PersonalBestAdapter
    private var swimmerId: Int = -1

    private val distances = listOf(50, 100, 200, 400, 800, 1500)
    private val strokes = StrokeType.values().map { it.name }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personal_bests)

        db = AppDatabase.getInstance(this)
        swimmerId = intent.getIntExtra("SWIMMER_ID", -1)

        if (swimmerId == -1) {
            finish()
            return
        }

        rvPersonalBests = findViewById(R.id.rvPersonalBests)
        emptyState = findViewById(R.id.emptyState)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnAddPB = findViewById<MaterialButton>(R.id.btnAddPB)

        rvPersonalBests.layoutManager = LinearLayoutManager(this)
        adapter = PersonalBestAdapter(
            onDelete = { pb -> deletePB(pb) }
        )
        rvPersonalBests.adapter = adapter

        btnBack.setOnClickListener {
            it.animateClick()
            finish()
        }

        btnAddPB.setOnClickListener {
            it.animateClick()
            showAddPBDialog()
        }

        loadPersonalBests()
    }

    private fun loadPersonalBests() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pbs = db.personalBestDao().getBySwimmerId(swimmerId)
            withContext(Dispatchers.Main) {
                if (pbs.isEmpty()) {
                    emptyState.visibility = View.VISIBLE
                    rvPersonalBests.visibility = View.GONE
                } else {
                    emptyState.visibility = View.GONE
                    rvPersonalBests.visibility = View.VISIBLE
                    adapter.submitList(pbs)
                }
            }
        }
    }

    private fun showAddPBDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_personal_best, null)
        val spinnerDistance = dialogView.findViewById<Spinner>(R.id.spinnerDistance)
        val spinnerStroke = dialogView.findViewById<Spinner>(R.id.spinnerStroke)
        val etMinutes = dialogView.findViewById<TextInputEditText>(R.id.etMinutes)
        val etSeconds = dialogView.findViewById<TextInputEditText>(R.id.etSeconds)

        // Setup distance spinner
        val distanceAdapter = ArrayAdapter(this, R.layout.spinner_item, distances.map { "${it}m" })
        distanceAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerDistance.adapter = distanceAdapter

        // Setup stroke spinner
        val strokeAdapter = ArrayAdapter(this, R.layout.spinner_item, strokes)
        strokeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerStroke.adapter = strokeAdapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            it.animateClick()
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            it.animateClick()
            val distance = distances[spinnerDistance.selectedItemPosition]
            val strokeType = StrokeType.valueOf(strokes[spinnerStroke.selectedItemPosition])
            val minutes = etMinutes.text.toString().toFloatOrNull() ?: 0f
            val seconds = etSeconds.text.toString().toFloatOrNull() ?: 0f
            val totalSeconds = minutes * 60 + seconds

            if (totalSeconds <= 0) {
                android.widget.Toast.makeText(this, "Please enter a valid time", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val pb = PersonalBest(
                    swimmerId = swimmerId,
                    distance = distance,
                    strokeType = strokeType,
                    bestTime = totalSeconds
                )
                db.personalBestDao().insert(pb)

                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    loadPersonalBests()
                    android.widget.Toast.makeText(
                        this@PersonalBestsActivity,
                        "Personal Best saved",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        dialog.show()
    }

    private fun deletePB(pb: PersonalBest) {
        AlertDialog.Builder(this)
            .setTitle("Delete Personal Best")
            .setMessage("Delete ${pb.distance}m ${pb.strokeType.name}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.personalBestDao().delete(pb)
                    withContext(Dispatchers.Main) {
                        loadPersonalBests()
                    }
                }
            }
            .show()
    }

    inner class PersonalBestAdapter(
        private val onDelete: (PersonalBest) -> Unit
    ) : RecyclerView.Adapter<PersonalBestAdapter.ViewHolder>() {

        private var items = listOf<PersonalBest>()

        fun submitList(newItems: List<PersonalBest>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_personal_best, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvEvent: TextView = view.findViewById(R.id.tvEvent)
            private val tvTime: TextView = view.findViewById(R.id.tvTime)
            private val tvUpdated: TextView = view.findViewById(R.id.tvUpdated)
            private val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)

            fun bind(pb: PersonalBest) {
                tvEvent.text = "${pb.distance}m ${pb.strokeType.name}"
                
                val minutes = (pb.bestTime / 60).toInt()
                val seconds = pb.bestTime % 60
                tvTime.text = String.format("%d:%05.2f", minutes, seconds)

                val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                tvUpdated.text = "Updated ${dateFormat.format(Date(pb.updatedAt))}"

                btnDelete.setOnClickListener {
                    it.animateClick()
                    onDelete(pb)
                }
            }
        }
    }
}
