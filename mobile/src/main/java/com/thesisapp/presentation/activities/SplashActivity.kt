package com.thesisapp.presentation.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import com.thesisapp.R
import com.thesisapp.utils.AuthManager
import com.thesisapp.utils.UserRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var supabase: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val hasSupabaseSession = supabase.auth.currentSessionOrNull() != null
            if (!hasSupabaseSession) {
                AuthManager.logout(this)
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
                return@postDelayed
            }

            if (!AuthManager.isLoggedIn(this)) {
                startActivity(Intent(this, AuthActivity::class.java))
                finish()
                return@postDelayed
            }

            val user = AuthManager.currentUser(this)
            if (user?.role == UserRole.SWIMMER) {
                var teamId = AuthManager.currentTeamId(this)
                if (teamId == null) {
                    val teams = AuthManager.getSwimmerTeams(this, user.email)
                    if (teams.isNotEmpty()) {
                        teamId = teams.first()
                        AuthManager.setCurrentTeamId(this, teamId)
                    }
                }
                val swimmerId = AuthManager.getLinkedSwimmerId(this, user.email, teamId)
                if (teamId != null && swimmerId != null) {
                    startActivity(
                        Intent(this, SwimmerProfileActivity::class.java).apply {
                            putExtra(SwimmerProfileActivity.EXTRA_SWIMMER_ID, swimmerId)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                    )
                    finish()
                    return@postDelayed
                }
            }

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1000)
    }
}