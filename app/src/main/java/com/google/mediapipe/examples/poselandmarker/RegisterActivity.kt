package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class RegisterActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        val usernameField = findViewById<EditText>(R.id.editTextUsername)
        val emailField = findViewById<EditText>(R.id.editTextEmail)
        val passwordField = findViewById<EditText>(R.id.editTextPassword)
        val registerButton = findViewById<Button>(R.id.buttonRegister)
        val loginButton = findViewById<Button>(R.id.buttonBackToLogin)

        registerButton.setOnClickListener {
            val username = usernameField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            // Get selected gender
            val gender = when {
                findViewById<RadioButton>(R.id.radioMale).isChecked -> "Male"
                findViewById<RadioButton>(R.id.radioFemale).isChecked -> "Female"
                findViewById<RadioButton>(R.id.radioOther).isChecked -> "Other"
                else -> "Not specified"
            }

            if (username.isEmpty()) {
                Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (email.length > 5 && password.length > 6) {
                registerUser(email, password, username, gender)
            } else {
                Toast.makeText(this, "Invalid email or password (too short)",
                    Toast.LENGTH_SHORT).show()
            }
        }

        loginButton.setOnClickListener {
            // Back to login
            finish()
        }
    }

    private fun registerUser(email: String, password: String, username: String, gender: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser

                    // Set display name in Firebase Auth
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()

                    user?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { profileTask ->
                            if (profileTask.isSuccessful) {
                                // Save additional info to database
                                saveUserData(user.uid, username, gender)
                            } else {
                                Toast.makeText(this, "Failed to set username",
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveUserData(userId: String, username: String, gender: String) {
        val userRef = database.getReference("users").child(userId)

        val userData = HashMap<String, Any>()
        userData["username"] = username
        userData["gender"] = gender
        userData["totalPoints"] = 0
        userData["createdAt"] = ServerValue.TIMESTAMP

        // Initialize attributes
        val attributes = HashMap<String, Any>()
        attributes["strength"] = 10f
        attributes["agility"] = 10f
        attributes["stamina"] = 10f
        userData["attributes"] = attributes

        // Initialize empty exercise stats
        val exercises = HashMap<String, Any>()
        val pushups = HashMap<String, Any>()
        pushups["count"] = 0
        pushups["points"] = 0
        pushups["bestStreak"] = 0
        exercises["pushups"] = pushups

        val plank = HashMap<String, Any>()
        plank["points"] = 0
        plank["totalSeconds"] = 0
        plank["bestDuration"] = 0
        exercises["plank"] = plank
        userData["exercises"] = exercises

        userRef.updateChildren(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save user data: ${e.message}",
                    Toast.LENGTH_LONG).show()
            }
    }
}
