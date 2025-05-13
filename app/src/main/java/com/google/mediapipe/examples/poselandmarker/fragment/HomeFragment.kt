package com.google.mediapipe.examples.poselandmarker.fragment

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.mediapipe.examples.poselandmarker.R
import com.google.firebase.auth.FirebaseAuth
import android.Manifest
import android.util.Log
import android.widget.ImageButton
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.mediapipe.examples.poselandmarker.achievements.AchievementManager
import com.google.mediapipe.examples.poselandmarker.challenges.DailyChallengeManager

class HomeFragment : Fragment() {

    private companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
    }

    // Existing view references
    private lateinit var healthProgressBar: ProgressBar
    private lateinit var healthTextView: TextView
    private lateinit var strengthTextView: TextView
    private lateinit var agilityTextView: TextView
    private lateinit var staminaTextView: TextView
    private lateinit var pushupTextView: TextView
    private lateinit var crunchesTextView: TextView
    private lateinit var plankTextView: TextView
    private lateinit var startWorkoutButton: Button
    private lateinit var leaderboardButton: Button
    private lateinit var nameTextView: TextView
    private lateinit var dailyChallengesButton: ImageButton
    // New view references from layout
    private lateinit var avatarImageView: ImageView


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize existing views
        healthProgressBar = view.findViewById(R.id.progressHealth)
        healthTextView = view.findViewById(R.id.textHealth)
        strengthTextView = view.findViewById(R.id.textStrength)
        agilityTextView = view.findViewById(R.id.textAgility)
        staminaTextView = view.findViewById(R.id.textStaminaStats)
        pushupTextView = view.findViewById(R.id.textPushup)
        crunchesTextView = view.findViewById(R.id.textCrunches)
        plankTextView = view.findViewById(R.id.textPlank)
        startWorkoutButton = view.findViewById(R.id.buttonStartWorkout)
        leaderboardButton = view.findViewById(R.id.buttonLeaderboard)
        nameTextView = view.findViewById(R.id.textView_Name)
        dailyChallengesButton = view.findViewById(R.id.buttonDailyChallenges)
        // Initialize new views
        avatarImageView = view.findViewById(R.id.avatarImage)
        // Get user data and update UI
        loadUserData()

        // Set up button click listeners
        startWorkoutButton.setOnClickListener {
            // Check camera permission before navigating
            if (hasCameraPermission()) {
                navigateToWorkout()
            } else {
                requestCameraPermission()
            }
        }
        // Set up button click listener
        dailyChallengesButton.setOnClickListener {
            showDailyChallenges()
        }

        leaderboardButton.setOnClickListener {
            // Navigate to leaderboard screen
             findNavController().navigate(R.id.action_homeFragment_to_leaderboardFragment)
        }

        // In onViewCreated
        val achievementsButton = view.findViewById<ImageButton>(R.id.buttonAchievements)
        achievementsButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_achievementsFragment)
        }


    }

    private fun showDailyChallenges() {
        // Display daily challenges
        findNavController().navigate(R.id.action_homeFragment_to_dailyChallengesFragment)
    }

    private fun navigateToWorkout() {
        findNavController().navigate(R.id.action_homeFragment_to_permissions_fragment)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Show explanation dialog
            AlertDialog.Builder(requireContext())
                .setTitle("Camera Permission Required")
                .setMessage("Camera access is needed to detect your workout poses")
                .setPositiveButton("Grant Permission") { _, _ ->
                    requestPermissions(
                        arrayOf(Manifest.permission.CAMERA),
                        CAMERA_PERMISSION_REQUEST_CODE
                    )
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted, navigate to camera
                    navigateToWorkout()
                } else {
                    // Permission denied, show message
                    Toast.makeText(
                        requireContext(),
                        "Camera permission is required for workouts",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun loadUserData() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid
        val userRef = FirebaseDatabase.getInstance().getReference("users").child(userId)

        // Load user profile information
        nameTextView.text = currentUser.displayName ?: "Fitness Warrior"

        // Set default values before data loads
        healthProgressBar.progress = 100
        healthTextView.text = "Health: 100/100"

        // Create an instance of AchievementManager
        val achievementManager = AchievementManager(FirebaseDatabase.getInstance())

        // Initialize all achievements so they show up as locked
        achievementManager.initializeAllAchievements(userId)

        // Initialize daily challenges - add this line
        val challengeManager = DailyChallengeManager(FirebaseDatabase.getInstance())
        challengeManager.checkAndGenerateChallenge(userId)

        // Load attributes (strength, agility, stamina)
        userRef.child("attributes").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get attributes with default fallback values
                val strength = snapshot.child("strength").getValue(Int::class.java) ?: 10
                val agility = snapshot.child("agility").getValue(Int::class.java) ?: 10
                val stamina = snapshot.child("stamina").getValue(Int::class.java) ?: 10

                // Update UI with attribute values
                strengthTextView.text = "Strength: $strength"
                agilityTextView.text = "Agility: $agility"
                staminaTextView.text = "Stamina: $stamina"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeFragment", "Failed to load attributes", error.toException())
            }
        })

        // Load exercise data for plank
        userRef.child("exercises").child("plank").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get plank stats with default fallback values
                val totalSeconds = snapshot.child("totalSeconds").getValue(Int::class.java) ?: 0
                val bestDuration = snapshot.child("bestDuration").getValue(Int::class.java) ?: 0
                val plankPoints = snapshot.child("points").getValue(Int::class.java) ?: 0
                // Format plank time for display
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                plankTextView.text = "Plank: ${plankPoints}"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeFragment", "Failed to load plank data", error.toException())
            }
        })

        // Load exercise data for push-ups
        userRef.child("exercises").child("pushups").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Get push-up stats with default fallback values
                val pushUpCount = snapshot.child("count").getValue(Int::class.java) ?: 0
                val bestStreak = snapshot.child("bestStreak").getValue(Int::class.java) ?: 0
                val pushUpPoints = snapshot.child("points").getValue(Int::class.java) ?: 0

                // Format push-up stats for display
                pushupTextView.text = "Push-ups: $pushUpPoints"

                // Optional: Add more detailed information with count
                // pushupTextView.text = "Push-ups: $pushUpPoints ($pushUpCount reps)"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeFragment", "Failed to load push-up data", error.toException())
            }
        })


        // Load total points
        userRef.child("totalPoints").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val points = snapshot.getValue(Int::class.java) ?: 0
                // Update points display or leaderboard stats
                // pointsTextView.text = "Points: $points"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeFragment", "Failed to load points", error.toException())
            }
        })
    }

}
