package com.thesisapp.presentation.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Goal
import com.thesisapp.data.non_dao.GoalProgress
import com.thesisapp.data.non_dao.GoalType
import com.thesisapp.data.non_dao.Swimmer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Calendar

class SwimmersAdapter(
    private var swimmers: MutableList<Swimmer>,
    private val onEditClick: (Swimmer) -> Unit,
    private val onDeleteClick: (Swimmer) -> Unit,
    private val onSwimmerClick: (Swimmer) -> Unit,
    private val isCoach: Boolean = true,
    private val teamId: Int
) : RecyclerView.Adapter<SwimmersAdapter.SwimmerViewHolder>() {

    // Memoization cache for calculated ages
    private val ageCache = mutableMapOf<String, Int>()
    private val goalCache = mutableMapOf<Int, Goal?>()

    class SwimmerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.swimmerName)
        val specialty: TextView = view.findViewById(R.id.swimmerSpecialty)
        val age: TextView = view.findViewById(R.id.swimmerAge)
        val height: TextView = view.findViewById(R.id.swimmerHeight)
        val weight: TextView = view.findViewById(R.id.swimmerWeight)
        val sex: TextView = view.findViewById(R.id.swimmerSex)
        val wingspan: TextView = view.findViewById(R.id.swimmerWingspan)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditSwimmer)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteSwimmer)

        // Goal progress views
        val goalProgressContainer: LinearLayout = view.findViewById(R.id.goalProgressContainer)
        val tvGoalProgress: TextView = view.findViewById(R.id.tvGoalProgress)
        val goalProgressIndicator: View = view.findViewById(R.id.goalProgressIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwimmerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_swimmer, parent, false)
        return SwimmerViewHolder(view)
    }

    override fun onBindViewHolder(holder: SwimmerViewHolder, position: Int) {
        val swimmer = swimmers[position]
        val context = holder.itemView.context

        holder.name.text = swimmer.name
        
        // Bind specialty
        if (!swimmer.specialty.isNullOrEmpty()) {
            holder.specialty.text = swimmer.specialty
            holder.specialty.visibility = View.VISIBLE
        } else {
            holder.specialty.visibility = View.GONE
        }

        holder.age.text = context.getString(R.string.swimmer_age, calculateAge(swimmer.birthday))
        holder.height.text = context.getString(R.string.swimmer_height, swimmer.height)
        holder.weight.text = context.getString(R.string.swimmer_weight, swimmer.weight)
        holder.sex.text = context.getString(R.string.swimmer_sex, swimmer.sex)
        holder.wingspan.text = context.getString(R.string.swimmer_wingspan, swimmer.wingspan)

        holder.itemView.setOnClickListener {
            onSwimmerClick(swimmer)
        }

        holder.btnEdit.visibility = if (isCoach) View.VISIBLE else View.GONE
        holder.btnDelete.visibility = if (isCoach) View.VISIBLE else View.GONE

        holder.btnEdit.setOnClickListener {
            onEditClick(swimmer)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(swimmer)
        }

        // Load and display goal progress
        loadGoalProgress(holder, swimmer)
    }

    private fun loadGoalProgress(holder: SwimmerViewHolder, swimmer: Swimmer) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.Companion.getInstance(holder.itemView.context)
            var goal = goalCache.getOrPut(swimmer.id) {
                db.goalDao().getActiveGoalForSwimmer(swimmer.id, teamId)
            }

            if (goal == null) {
                withContext(Dispatchers.Main) {
                    holder.goalProgressContainer.visibility = View.GONE
                }
                return@launch
            }

            // Get latest progress point
            val progressPoints = db.goalProgressDao().getProgressForGoal(goal.id)
            val latestProgress = progressPoints.lastOrNull()

            withContext(Dispatchers.Main) {
                if (latestProgress != null) {

                    // Calculate difference from goal
                    val currentTime = timeStringToSeconds(latestProgress.projectedRaceTime)
                    val goalTime = timeStringToSeconds(goal.goalTime)
                    val difference = currentTime - goalTime

                    // Format display
                    val diffText = when {
                        difference == 0f -> "On pace"
                        difference > 0 -> "+${String.format("%.1f", difference)}s"
                        else -> "${String.format("%.1f", difference)}s"
                    }

                    holder.tvGoalProgress.text = diffText

                    // Set color based on progress
                    val color = when {
                        difference < 0 -> ContextCompat.getColor(
                            holder.itemView.context,
                            R.color.accent
                        ) // Green - beating goal
                        difference <= 1.0 -> ContextCompat.getColor(
                            holder.itemView.context,
                            android.R.color.holo_orange_dark
                        ) // Orange - close
                        else -> ContextCompat.getColor(
                            holder.itemView.context,
                            R.color.error
                        ) // Red - far from goal
                    }

                    holder.tvGoalProgress.setTextColor(color)
                    holder.goalProgressIndicator.backgroundTintList = ColorStateList.valueOf(color)
                    holder.goalProgressContainer.visibility = View.VISIBLE
                } else {
                    holder.goalProgressContainer.visibility = View.GONE
                }
            }
        }
    }

    private fun timeStringToSeconds(timeString: String): Float {
        // Convert "1:02.50" or "0:58.00" to seconds as float
        val parts = timeString.split(":")
        if (parts.size != 2) return 0f
        val minutes = parts[0].toFloatOrNull() ?: 0f
        val seconds = parts[1].toFloatOrNull() ?: 0f
        return minutes * 60 + seconds
    }

    override fun getItemCount() = swimmers.size

    private fun calculateAge(birthday: String): Int {
        // Check cache first (memoization)
        return ageCache.getOrPut(birthday) {
            try {
                val birthDate = LocalDate.parse(birthday, DateTimeFormatter.ISO_LOCAL_DATE)
                val currentDate = LocalDate.now()
                Period.between(birthDate, currentDate).years
            } catch (e: Exception) {
                0
            }
        }
    }

    fun removeSwimmer(swimmer: Swimmer) {
        val position = swimmers.indexOf(swimmer)
        if (position != -1) {
            swimmers.removeAt(position)
            notifyItemRemoved(position)
            // Clean up cache entries
            ageCache.remove(swimmer.birthday)
            goalCache.remove(swimmer.id)
        }
    }

    fun updateSwimmers(newSwimmers: List<Swimmer>) {
        swimmers.clear()
        swimmers.addAll(newSwimmers)
        // Clear caches when swimmers list is updated
        ageCache.clear()
        goalCache.clear()
        notifyDataSetChanged()
    }
}