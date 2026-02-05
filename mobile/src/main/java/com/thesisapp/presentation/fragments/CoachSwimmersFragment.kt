package com.thesisapp.presentation.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.data.repository.TeamSyncRepository
import com.thesisapp.presentation.activities.CoachSwimmerProfileActivity
import com.thesisapp.presentation.activities.CreateSwimmerProfileActivity
import com.thesisapp.presentation.adapters.SwimmersAdapter
import com.thesisapp.utils.AuthManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class CoachSwimmersFragment : Fragment() {

    @Inject
    lateinit var teamSyncRepository: TeamSyncRepository

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabAddSwimmer: ExtendedFloatingActionButton
    private lateinit var adapter: SwimmersAdapter
    private lateinit var db: AppDatabase
    private lateinit var progressSync: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coach_swimmers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.Companion.getInstance(requireContext())
        recyclerView = view.findViewById(R.id.swimmersRecyclerView)
        fabAddSwimmer = view.findViewById(R.id.fabAddSwimmer)
        progressSync = view.findViewById(R.id.progressSyncSwimmers)

        val teamId = AuthManager.currentTeamId(requireContext()) ?: -1

        adapter = SwimmersAdapter(
            swimmers = mutableListOf(),
            teamId = teamId,
            onEditClick = { swimmer ->
                // Open edit swimmer UI
                val intent = Intent(requireContext(), CreateSwimmerProfileActivity::class.java)
                intent.putExtra("SWIMMER_ID", swimmer.id)
                startActivity(intent)
            },
            onDeleteClick = { swimmer ->
                // Delete swimmer
                lifecycleScope.launch(Dispatchers.IO) {
                    db.swimmerDao().deleteSwimmer(swimmer)
                    withContext(Dispatchers.Main) { loadSwimmers() }
                }
            },
            onSwimmerClick = { swimmer ->
                val intent = Intent(requireContext(), CoachSwimmerProfileActivity::class.java)
                intent.putExtra(CoachSwimmerProfileActivity.Companion.EXTRA_SWIMMER, swimmer)
                startActivity(intent)
            },
            isCoach = true
        )
        recyclerView.adapter = adapter

        fabAddSwimmer.setOnClickListener {
            showInviteSwimmerDialog()
        }

        refreshSwimmers()
    }

    override fun onResume() {
        super.onResume()
        refreshSwimmers()
    }

    private fun refreshSwimmers() {
        val teamId = AuthManager.currentTeamId(requireContext()) ?: return

        lifecycleScope.launch {
            progressSync.visibility = View.VISIBLE
            try {
                withContext(Dispatchers.IO) {
                    teamSyncRepository.syncTeamMembers(teamId)
                }
            } catch (e: Exception) {
                Log.e("CoachSwimmers", "Failed to sync team members (teamId=$teamId)", e)
                val msg = e.message?.takeIf { it.isNotBlank() }
                Toast.makeText(
                    requireContext(),
                    msg?.let { "Sync failed — showing cached data\n$it" }
                        ?: "Sync failed — showing cached data",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                progressSync.visibility = View.GONE
            }

            loadSwimmers()
        }
    }

    private fun loadSwimmers() {
        val teamId = AuthManager.currentTeamId(requireContext()) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var swimmers = db.teamMembershipDao().getSwimmersForTeam(teamId)

                withContext(Dispatchers.Main) {
                    adapter.updateSwimmers(swimmers)
                }
            } catch (e: Exception) {
                Log.e("CoachSwimmers", "Failed to load swimmers from local DB (teamId=$teamId)", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to load swimmers",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showInviteSwimmerDialog() {
        val teamId = AuthManager.currentTeamId(requireContext()) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            val team = db.teamDao().getById(teamId)
            withContext(Dispatchers.Main) {
                if (team != null) {
                    val message =
                        "Invite swimmers to join ${team.name}\n\nTeam Code: ${team.joinCode}\n\nShare this code with swimmers. They should:\n1. Open DORY app\n2. Tap 'Join Team'\n3. Enter this code\n4. Complete their swimmer profile"

                    AlertDialog.Builder(requireContext())
                        .setTitle("Invite Swimmer")
                        .setMessage(message)
                        .setPositiveButton("Copy Code") { _, _ ->
                            val clipboard =
                                requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Team Code", team.joinCode)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(requireContext(), "Code copied!", Toast.LENGTH_SHORT)
                                .show()
                        }
                        .setNeutralButton("Share") { _, _ ->
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"           // or: setType("text/plain")
                                putExtra(Intent.EXTRA_SUBJECT, "Join ${team.name} as a Swimmer")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Join my swimming team on DORY!\n\nTeam: ${team.name}\nCode: ${team.joinCode}\n\nDownload DORY, tap 'Join Team', and enter this code to create your swimmer profile."
                                )
                            }
                            startActivity(Intent.createChooser(shareIntent, "Invite Swimmer"))
                        }
                        .setNegativeButton("Close", null)
                        .show()
                } else {
                    Toast.makeText(requireContext(), "Team not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}