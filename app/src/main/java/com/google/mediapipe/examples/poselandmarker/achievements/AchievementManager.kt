package com.google.mediapipe.examples.poselandmarker.achievements

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener

class AchievementManager(private val database: FirebaseDatabase) {

    private val TAG = "AchievementManager"

    fun unlockAchievement(userId: String, achievementId: String, data: Map<String, Any>) {
        val userRef = database.reference.child("users").child(userId)

        // First ensure achievements node exists
        userRef.child("achievements").get()
            .addOnSuccessListener { achievementsSnapshot ->
                if (!achievementsSnapshot.exists()) {
                    userRef.child("achievements").setValue(HashMap<String, Any>())
                }

                // Then proceed with achievement unlocking
                val achievementRef = userRef.child("achievements").child(achievementId)

                achievementRef.get().addOnSuccessListener { snapshot ->
                    val isUnlocked = if (snapshot.exists()) {
                        snapshot.child("isUnlocked").getValue(Boolean::class.java) ?: false
                    } else {
                        false
                    }

                    if (!snapshot.exists() || !isUnlocked) {
                        // Achievement not unlocked yet
                        val achievementData = HashMap<String, Any>()
                        achievementData["isUnlocked"] = true
                        achievementData["timestamp"] = ServerValue.TIMESTAMP
                        achievementData.putAll(data)

                        achievementRef.updateChildren(achievementData)
                            .addOnSuccessListener {
                                Log.d(TAG, "Achievement unlocked: $achievementId")
                                // Award points for achievement
                                awardAchievementPoints(userId, 25) // 25 points per achievement

                                // Update achievement count in leaderboard
                                val leaderboardRef = database.reference.child("leaderboard").child(userId)
                                leaderboardRef.child("achievementCount").runTransaction(object : Transaction.Handler {
                                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                                        val currentCount = mutableData.getValue(Int::class.java) ?: 0
                                        mutableData.value = currentCount + 1
                                        return Transaction.success(mutableData)
                                    }

                                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                                        if (error != null) {
                                            Log.e(TAG, "Failed to update achievement count in leaderboard", error.toException())
                                        } else {
                                            Log.d(TAG, "Successfully updated achievement count in leaderboard")
                                        }
                                    }
                                })
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to unlock achievement: $achievementId", e)
                            }
                    } else {
                        Log.d(TAG, "Achievement already unlocked: $achievementId")
                    }
                }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to check achievement status: $achievementId", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to check achievements node for user: $userId", e)
            }
    }


    private fun awardAchievementPoints(userId: String, points: Int) {
        val userRef = database.reference.child("users").child(userId)
        userRef.child("totalPoints").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentPoints = mutableData.getValue(Int::class.java) ?: 0
                mutableData.value = currentPoints + points
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                // Handle completion
            }
        })
    }

    fun getAllAchievements(userId: String, callback: (List<Achievement>) -> Unit) {
        val achievementsRef = database.reference.child("users").child(userId).child("achievements")
        achievementsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val achievements = mutableListOf<Achievement>()

                // Create a map of all possible achievements first
                val allPossibleAchievements = HashMap<String, Achievement>()

                // Add push-up achievements
                for ((id, info) in AchievementConstants.PUSHUP_ACHIEVEMENTS) {
                    allPossibleAchievements[id] = Achievement(
                        id = id,
                        title = info.title,
                        description = info.description,
                        exerciseType = "pushup",
                        isUnlocked = false
                    )
                }

                // Add plank achievements
                for ((id, info) in AchievementConstants.PLANK_ACHIEVEMENTS) {
                    allPossibleAchievements[id] = Achievement(
                        id = id,
                        title = info.title,
                        description = info.description,
                        exerciseType = "plank",
                        isUnlocked = false
                    )
                }

                // Override with actual database values
                for (achievementSnapshot in snapshot.children) {
                    val id = achievementSnapshot.key ?: continue
                    val isUnlocked = achievementSnapshot.child("isUnlocked").getValue(Boolean::class.java) ?: false
                    val title = achievementSnapshot.child("title").getValue(String::class.java) ?: ""
                    val description = achievementSnapshot.child("description").getValue(String::class.java) ?: ""
                    val exerciseType = achievementSnapshot.child("exerciseType").getValue(String::class.java) ?: ""
                    val timestamp = achievementSnapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                    // Manually create the Achievement object with correct values
                    val achievement = Achievement(
                        id = id,
                        title = title,
                        description = description,
                        exerciseType = exerciseType,
                        timestamp = timestamp,
                        isUnlocked = isUnlocked  // This ensures the correct unlocked status
                    )

                    allPossibleAchievements[id] = achievement
                }



                // Convert map to list
                achievements.addAll(allPossibleAchievements.values)

                callback(achievements)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error loading achievements", error.toException())
                callback(emptyList())
            }
        })
    }



    fun initializeAllAchievements(userId: String) {
        val userRef = database.reference.child("users").child(userId)

        // Initialize push-up achievements
        for ((id, info) in AchievementConstants.PUSHUP_ACHIEVEMENTS) {
            val achievementRef = userRef.child("achievements").child(id)
            achievementRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val achievementData = HashMap<String, Any>()
                    achievementData["id"] = id
                    achievementData["title"] = info.title
                    achievementData["description"] = info.description
                    achievementData["exerciseType"] = "pushup"
                    achievementData["isUnlocked"] = false
                    achievementData["timestamp"] = ServerValue.TIMESTAMP

                    achievementRef.setValue(achievementData)
                }
            }
        }

        // Initialize plank achievements
        for ((id, info) in AchievementConstants.PLANK_ACHIEVEMENTS) {
            val achievementRef = userRef.child("achievements").child(id)
            achievementRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val achievementData = HashMap<String, Any>()
                    achievementData["id"] = id
                    achievementData["title"] = info.title
                    achievementData["description"] = info.description
                    achievementData["exerciseType"] = "plank"
                    achievementData["isUnlocked"] = false
                    achievementData["timestamp"] = ServerValue.TIMESTAMP

                    achievementRef.setValue(achievementData)
                }
            }
        }
    }

    fun updateLeaderboardAchievementCount(userId: String) {
        val achievementsRef = database.reference.child("users").child(userId).child("achievements")
        achievementsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var unlockedCount = 0
                for (achievementSnapshot in snapshot.children) {
                    val isUnlocked = achievementSnapshot.child("isUnlocked").getValue(Boolean::class.java) ?: false
                    if (isUnlocked) {
                        unlockedCount++
                    }
                }

                // Update the leaderboard entry
                val leaderboardRef = database.reference.child("leaderboard").child(userId)
                leaderboardRef.child("achievementCount").setValue(unlockedCount)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error counting achievements", error.toException())
            }
        })
    }


}
