package com.thesisapp.presentation.fragments

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.non_dao.Exercise
import com.thesisapp.data.non_dao.Swimmer
import com.thesisapp.presentation.activities.CreateExerciseActivity
import com.thesisapp.presentation.adapters.ExerciseAdapter
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SwimmerExerciseLibraryFragment : Fragment() {

    private var swimmer: Swimmer? = null
    private lateinit var db: AppDatabase
    private var exercises = listOf<Exercise>()

    private lateinit var exercisesRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var fabAddExercise: FloatingActionButton
    private var exerciseAdapter: ExerciseAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            swimmer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelable(ARG_SWIMMER, Swimmer::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelable(ARG_SWIMMER)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_swimmer_exercise_library, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.Companion.getInstance(requireContext())

        exercisesRecyclerView = view.findViewById(R.id.exercisesRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        fabAddExercise = view.findViewById(R.id.fabAddExercise)

        exercisesRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        fabAddExercise.setOnClickListener {
            val intent = Intent(requireContext(), CreateExerciseActivity::class.java)
            startActivity(intent)
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        val swimmerLocal = swimmer ?: return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            exercises = mutableListOf<Exercise>().apply {
                // Load team exercises if team is selected
                val teamId = AuthManager.currentTeamId(requireContext())
                if (teamId != null) {
                    // Filter by swimmer's category (SPRINT or DISTANCE)
                    addAll(db.exerciseDao().getExercisesForTeam(teamId)
                        .filter { it.category == swimmerLocal.category })
                }

                // Also load personal exercises (teamId = -1), also filtered by category
                addAll(db.exerciseDao().getExercisesForTeam(-1)
                    .filter { it.category == swimmerLocal.category })
            }

            withContext(Dispatchers.Main) {
                if (exercises.isEmpty()) {
                    exercisesRecyclerView.visibility = View.GONE
                    emptyStateLayout.visibility = View.VISIBLE
                } else {
                    exercisesRecyclerView.visibility = View.VISIBLE
                    emptyStateLayout.visibility = View.GONE

                    exerciseAdapter = ExerciseAdapter(
                        exercises.toMutableList(),
                        onEditClick = { exercise ->
                            // Only allow editing personal exercises
                            if (exercise.teamId == -1) {
                                val intent =
                                    Intent(requireContext(), CreateExerciseActivity::class.java)
                                intent.putExtra("EXERCISE_ID", exercise.id)
                                startActivity(intent)
                            }
                        },
                        onDeleteClick = { exercise ->
                            // Only allow deleting personal exercises
                            if (exercise.teamId == -1) {
                                deleteExercise(exercise)
                            }
                        }
                    )
                    exercisesRecyclerView.adapter = exerciseAdapter
                }
            }
        }
    }

    private fun deleteExercise(exercise: Exercise) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            db.exerciseDao().delete(exercise)
            withContext(Dispatchers.Main) {
                loadData()
            }
        }
    }

    companion object {
        private const val ARG_SWIMMER = "swimmer"

        fun newInstance(swimmer: Swimmer) = SwimmerExerciseLibraryFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_SWIMMER, swimmer)
            }
        }
    }
}