package com.thesisapp.presentation.fragments

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.thesisapp.R
import com.thesisapp.data.non_dao.Goal
import com.thesisapp.data.non_dao.GoalType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SetGoalDialogFragment : DialogFragment() {

    private var startDateMillis: Long = System.currentTimeMillis()
    private var endDateMillis: Long = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000) // 30 days from now

    private var existingGoal: Goal? = null
    private var swimmerId: Int = 0
    private var teamId: Int = 0

    var onGoalSaved: ((Goal) -> Unit)? = null

    companion object {
        private const val ARG_SWIMMER_ID = "swimmer_id"
        private const val ARG_TEAM_ID = "team_id"
        private const val ARG_EXISTING_GOAL = "existing_goal"

        fun newInstance(swimmerId: Int, teamId: Int, existingGoal: Goal? = null): SetGoalDialogFragment {
            val fragment = SetGoalDialogFragment()
            val args = Bundle().apply {
                putInt(ARG_SWIMMER_ID, swimmerId)
                putInt(ARG_TEAM_ID, teamId)
                existingGoal?.let {
                    putInt("goal_id", it.id)
                    putString("event_name", it.eventName)
                    putString("goal_time", it.goalTime)
                    putLong("start_date", it.startDate)
                    putLong("end_date", it.endDate)
                    putString("goal_type", it.goalType.name)
                }
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_ThesisApp)

        arguments?.let {
            swimmerId = it.getInt(ARG_SWIMMER_ID)
            teamId = it.getInt(ARG_TEAM_ID)

            // Check if editing existing goal
            if (it.containsKey("goal_id")) {
                existingGoal = Goal(
                    id = it.getInt("goal_id"),
                    swimmerId = swimmerId,
                    teamId = teamId,
                    eventName = it.getString("event_name", ""),
                    goalTime = it.getString("goal_time", ""),
                    startDate = it.getLong("start_date"),
                    endDate = it.getLong("end_date"),
                    goalType = GoalType.valueOf(it.getString("goal_type", "SPRINT"))
                )
                startDateMillis = existingGoal!!.startDate
                endDateMillis = existingGoal!!.endDate
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_set_goal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inputEventName = view.findViewById<TextInputEditText>(R.id.inputEventName)
        val inputGoalTime = view.findViewById<TextInputEditText>(R.id.inputGoalTime)
        val inputGoalType = view.findViewById<AutoCompleteTextView>(R.id.inputGoalType)
        val btnSelectStartDate = view.findViewById<Button>(R.id.btnSelectStartDate)
        val btnSelectEndDate = view.findViewById<Button>(R.id.btnSelectEndDate)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSaveGoal)

        // Setup goal type dropdown
        val goalTypes = arrayOf("Sprint", "Endurance")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, goalTypes)
        inputGoalType.setAdapter(adapter)

        // Pre-fill if editing
        existingGoal?.let { goal ->
            inputEventName.setText(goal.eventName)
            inputGoalTime.setText(goal.goalTime)
            inputGoalType.setText(goal.goalType.name.lowercase().replaceFirstChar { it.uppercase() }, false)
        }

        // Update button texts
        updateDateButtonTexts(btnSelectStartDate, btnSelectEndDate)

        // Start date picker
        btnSelectStartDate.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = startDateMillis }
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    startDateMillis = calendar.timeInMillis
                    updateDateButtonTexts(btnSelectStartDate, btnSelectEndDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // End date picker
        btnSelectEndDate.setOnClickListener {
            val calendar = Calendar.getInstance().apply { timeInMillis = endDateMillis }
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    endDateMillis = calendar.timeInMillis
                    updateDateButtonTexts(btnSelectStartDate, btnSelectEndDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnSave.setOnClickListener {
            val eventName = inputEventName.text.toString().trim()
            val goalTime = inputGoalTime.text.toString().trim()
            val goalTypeStr = inputGoalType.text.toString().trim()

            if (eventName.isEmpty() || goalTime.isEmpty() || goalTypeStr.isEmpty()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (endDateMillis <= startDateMillis) {
                Toast.makeText(context, "Deadline must be after start date", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val goalType = when (goalTypeStr.lowercase()) {
                "sprint" -> GoalType.SPRINT
                "endurance" -> GoalType.ENDURANCE
                else -> GoalType.SPRINT
            }

            val goal = Goal(
                id = existingGoal?.id ?: 0,
                swimmerId = swimmerId,
                teamId = teamId,
                eventName = eventName,
                goalTime = goalTime,
                startDate = startDateMillis,
                endDate = endDateMillis,
                goalType = goalType
            )

            onGoalSaved?.invoke(goal)
            dismiss()
        }
    }

    private fun updateDateButtonTexts(startButton: Button, endButton: Button) {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        startButton.text = dateFormat.format(Date(startDateMillis))
        endButton.text = dateFormat.format(Date(endDateMillis))
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}