package com.thesisapp.presentation

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thesisapp.R
import com.thesisapp.data.AppDatabase
import com.thesisapp.utils.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EnrollViaCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enroll_via_code)

        val inputCode = findViewById<EditText>(R.id.inputSwimmerCode)
        val btnEnroll = findViewById<Button>(R.id.btnEnroll)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        btnEnroll.setOnClickListener {
            val code = inputCode.text.toString().trim().uppercase()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter team code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getInstance(this@EnrollViaCodeActivity)
                val team = db.teamDao().getByJoinCode(code)
                
                withContext(Dispatchers.Main) {
                    if (team == null) {
                        Toast.makeText(this@EnrollViaCodeActivity, "Invalid team code", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    
                    val user = AuthManager.currentUser(this@EnrollViaCodeActivity)
                    if (user == null) {
                        Toast.makeText(this@EnrollViaCodeActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }
                    
                    // Show confirmation dialog
                    AlertDialog.Builder(this@EnrollViaCodeActivity)
                        .setTitle("Join ${team.name}?")
                        .setMessage("You're about to join ${team.name}. You'll create your swimmer profile next.")
                        .setPositiveButton("Continue") { _, _ ->
                            // Redirect to create swimmer profile
                            val intent = Intent(this@EnrollViaCodeActivity, CreateSwimmerProfileActivity::class.java)
                            intent.putExtra("TEAM_ID", team.id)
                            startActivity(intent)
                            finish()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }
    
    // Hide keyboard when touching outside of EditText
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val view = currentFocus
            if (view != null) {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

