package com.google.mediapipe.examples.poselandmarker.challenges

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class DailyChallengeManager(private val database: FirebaseDatabase) {

    // Create a new challenge for a user
    fun generateDailyChallenge(userId: String) {
        val challengeRef = database.reference.child("users").child(userId).child("challenges").push()
        val challengeId = challengeRef.key ?: return

        // Generate a random challenge
        val challenge = createRandomChallenge(challengeId)

        // Save to Firebase
        challengeRef.setValue(challenge)
    }

    // Create a random challenge based on exercise types
    private fun createRandomChallenge(id: String): DailyChallenge {
        val exerciseTypes = listOf("pushup", "plank", "crunches")
        val exerciseType = exerciseTypes.random()

        val title: String
        val description: String
        val targetCount: Int

        when (exerciseType) {
            "pushup" -> {
                val count = (5..30 step 5).toList().random()
                title = "Push-up Challenge"
                description = "Complete $count push-ups in a single workout"
                targetCount = count
            }
            "plank" -> {
                val seconds = (30..120 step 15).toList().random()
                title = "Plank Challenge"
                description = "Hold a plank for $seconds seconds"
                targetCount = seconds
            }
            else -> {
                val count = (10..50 step 10).toList().random()
                title = "Crunches Challenge"
                description = "Complete $count crunches in a single workout"
                targetCount = count
            }
        }

        return DailyChallenge(
            id = id,
            title = title,
            description = description,
            exerciseType = exerciseType,
            targetCount = targetCount,
            pointsReward = calculateReward(exerciseType, targetCount),
            completed = false,
            dateCreated = System.currentTimeMillis()
        )
    }

    // Calculate reward points based on difficulty
    private fun calculateReward(exerciseType: String, targetCount: Int): Int {
        return when (exerciseType) {
            "pushup" -> targetCount / 5 * 10
            "plank" -> targetCount / 10 * 5
            else -> targetCount / 10 * 8
        }
    }

    // Check and generate a new daily challenge if needed
    fun checkAndGenerateChallenge(userId: String) {
        val userChallengesRef = database.reference.child("users").child(userId).child("challenges")

        userChallengesRef.orderByChild("completed").equalTo(false).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists() || snapshot.childrenCount.toInt() == 0) {
                    // No active challenges found, generate a new one
                    generateDailyChallenge(userId)
                    Log.d("DailyChallengeManager", "Generated new challenge - no active challenges found")
                } else {
                    Log.d("DailyChallengeManager", "Active challenge exists, not generating new one")
                }
            }
            .addOnFailureListener { error ->
                Log.e("DailyChallengeManager", "Error checking challenges", error)
            }
    }

    // Complete a challenge
    fun completeChallenge(challengeId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // First mark the challenge as completed
        val challengeRef = database.reference
            .child("users").child(userId).child("challenges").child(challengeId)

        // Use a batch update to ensure all fields update atomically
        val updates = HashMap<String, Any>()
        updates["completed"] = true
        updates["progress"] = 100 // Make sure progress shows as 100%
        updates["completedDate"] = ServerValue.TIMESTAMP

        challengeRef.updateChildren(updates)
            .addOnSuccessListener {
                Log.d("DailyChallengeManager", "Challenge marked as completed")
                // Only generate a new challenge after confirming the current one is completed
                checkAndGenerateChallenge(userId)
            }
            .addOnFailureListener { e ->
                Log.e("DailyChallengeManager", "Error completing challenge", e)
            }
    }

    // Get all user challenges for display
    fun getUserChallenges(userId: String, callback: (List<DailyChallenge>) -> Unit) {
        val challengesRef = database.reference.child("users").child(userId).child("challenges")

        challengesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val challenges = mutableListOf<DailyChallenge>()
                for (challengeSnapshot in snapshot.children) {
                    challengeSnapshot.getValue(DailyChallenge::class.java)?.let {
                        challenges.add(it)
                    }
                }
                challenges.sortByDescending { !it.completed }
                callback(challenges)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DailyChallengeManager", "Error loading challenges", error.toException())
                callback(emptyList())
            }
        })
    }
}

