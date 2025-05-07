package com.google.mediapipe.examples.poselandmarker.exercise

import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.mediapipe.examples.poselandmarker.excercise.AngleHelper
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.max

/**
 * PushUpHelper: Detects push-up exercise positions and tracks performance
 * with Firebase integration for persistent storage and gamification.
 */
class PushUpHelper(
    private var userId: String,
    private val database: FirebaseDatabase,
    private val pushUpOverlayText: TextView,
    private val context: Context? = null
) {
    // State management
    private var isAtTop = false
    private var isAtBottom = false
    private var lastPositionChangeTime = 0L
    private var sessionStartTime = System.currentTimeMillis()
    private var consecutiveReps = 0
    private var maxConsecutiveReps = 0

    // Exercise metrics
    private var pushUpCount = 0
    private val pointsPerPushUp = 10
    private val strengthIncrementPerPushUp = 0.5f
    private var caloriesBurned = 0.0

    // Detection configuration
    private val topThreshold = 150f
    private val bottomThreshold = 115f

    // Database references
    private lateinit var userRef: DatabaseReference
    private lateinit var pushUpRef: DatabaseReference
    private lateinit var strengthRef: DatabaseReference
    private lateinit var analyticsRef: DatabaseReference

    init {
        // Add this check to ensure userId is valid before initializing
        if (userId.isNotEmpty()) {
            initializeDatabaseReferences()
            verifyDatabaseStructure()
        } else {
            Log.e(TAG, "User ID is empty, cannot initialize database references")
        }
    }

    // Detection helper
    private val angleHelper = AngleHelper()

    // Constants
    companion object {
        private const val TAG = "PushUpHelper"
        private const val DATABASE_RETRY_ATTEMPTS = 3
        private const val CALORIES_PER_PUSHUP = 0.4
        private const val MIN_DATABASE_UPDATE_INTERVAL = 5000L
    }

    // Processing state
    private var isProcessing = false
    private var lastDatabaseUpdateTime = 0L



    /**
     * Initialize Firebase database references for frequently accessed paths
     */
    private fun initializeDatabaseReferences() {
        userRef = database.reference.child("users").child(userId)
        pushUpRef = userRef.child("exercises").child("pushups")
        strengthRef = userRef.child("attributes").child("strength")
        analyticsRef = userRef.child("analytics")
    }

    /**
     * Verify and create necessary database structure for new users
     */
    private fun verifyDatabaseStructure() {
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.hasChild("attributes") ||
                    !snapshot.hasChild("exercises") ||
                    !snapshot.child("exercises").hasChild("pushups") ||
                    !snapshot.hasChild("analytics")) {

                    // Create initial data structure
                    val updates = HashMap<String, Any>()

                    // Initialize attributes if missing
                    if (!snapshot.hasChild("attributes")) {
                        val attributes = HashMap<String, Any>()
                        attributes["strength"] = 10f
                        attributes["agility"] = 10f
                        attributes["stamina"] = 10f
                        updates["attributes"] = attributes
                    }

                    // Initialize exercises/pushups if missing
                    if (!snapshot.hasChild("exercises") ||
                        !snapshot.child("exercises").hasChild("pushups")) {
                        val pushups = HashMap<String, Any>()
                        pushups["count"] = 0
                        pushups["points"] = 0
                        pushups["bestStreak"] = 0
                        updates["exercises/pushups"] = pushups
                    }

                    // Initialize analytics if missing
                    if (!snapshot.hasChild("analytics")) {
                        val analytics = HashMap<String, Any>()
                        analytics["lastWorkout"] = ServerValue.TIMESTAMP
                        analytics["totalCaloriesBurned"] = 0.0
                        analytics["workoutStreak"] = 0
                        updates["analytics"] = analytics
                    }

                    // Set initial total points if missing
                    if (!snapshot.hasChild("totalPoints")) {
                        updates["totalPoints"] = 0
                    }

                    // Apply updates if needed
                    if (updates.isNotEmpty()) {
                        userRef.updateChildren(updates)
                            .addOnSuccessListener {
                                Log.d(TAG, "Database structure initialized successfully")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to initialize database structure: ${e.message}")

                            }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database verification failed: ${error.message}")
            }
        })
    }

    /**
     * Process landmarks from MediaPipe pose detection to identify push-up positions
     */
    fun processLandmarks(landmarks: List<NormalizedLandmark>) {
        // Prevent concurrent processing
        if (isProcessing) return
        isProcessing = true

        // Ensure there are enough landmarks
        if (landmarks.size < 17) {
            isProcessing = false
            return
        }

        try {
            // Extract the relevant landmarks
            val leftShoulder = AngleHelper.PointF(landmarks[11].x(), landmarks[11].y())
            val rightShoulder = AngleHelper.PointF(landmarks[12].x(), landmarks[12].y())
            val leftElbow = AngleHelper.PointF(landmarks[13].x(), landmarks[13].y())
            val rightElbow = AngleHelper.PointF(landmarks[14].x(), landmarks[14].y())
            val leftWrist = AngleHelper.PointF(landmarks[15].x(), landmarks[15].y())
            val rightWrist = AngleHelper.PointF(landmarks[16].x(), landmarks[16].y())

            // Calculate elbow angles directly
            val leftElbowAngle = angleHelper.calculateAngle(leftShoulder, leftElbow, leftWrist)
            val rightElbowAngle = angleHelper.calculateAngle(rightShoulder, rightElbow, rightWrist)

            // Determine positions
            val detectedTop = leftElbowAngle > topThreshold && rightElbowAngle > topThreshold
            val detectedBottom = leftElbowAngle < bottomThreshold && rightElbowAngle < bottomThreshold

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Angles: L=$leftElbowAngle, R=$rightElbowAngle")
                Log.d(TAG, "Detected: Top=$detectedTop, Bottom=$detectedBottom")
            }

            // Handle state transitions
            handlePushUpState(detectedTop, detectedBottom)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing landmarks: ${e.message}")

        } finally {
            isProcessing = false
        }
    }

    /**
     * Enhanced state machine for push-up detection with better debouncing
     */
    // Add this flag to your class variables
    private var readyForNextRep = false

    private fun handlePushUpState(detectedTop: Boolean, detectedBottom: Boolean) {
        val currentTime = System.currentTimeMillis()

        // If we've completed a push-up and returned to top, we're ready for next rep
        if (detectedTop && !isAtBottom && !readyForNextRep) {
            readyForNextRep = true
            Log.d(TAG, "Ready for next rep")
        }

        // TOP POSITION DETECTION - only if we're not already tracking a push-up
        if (detectedTop && !isAtTop && !isAtBottom && readyForNextRep) {
            isAtTop = true
            lastPositionChangeTime = currentTime
            updateOverlayText("Top position detected", android.R.color.holo_blue_light)
            Log.d(TAG, "Top position detected")
        }

        // BOTTOM POSITION DETECTION - only if we've already detected top position
        if (detectedBottom && isAtTop && !isAtBottom) {
            isAtBottom = true
            lastPositionChangeTime = currentTime
            updateOverlayText("Bottom position detected", android.R.color.holo_red_light)
            Log.d(TAG, "Bottom position detected")
        }

        // COMPLETE PUSH-UP - when both positions have been detected
        if (isAtTop && isAtBottom) {
            pushUpCount++
            consecutiveReps++
            maxConsecutiveReps = max(maxConsecutiveReps, consecutiveReps)

            // Update UI with animated counter
            animateCounterUpdate(pushUpCount)

            // Award points (with throttling)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDatabaseUpdateTime > MIN_DATABASE_UPDATE_INTERVAL) {
                awardPushUpPoints(pointsPerPushUp * consecutiveReps)
                lastDatabaseUpdateTime = currentTime
            }

            

            checkForAchievements()
            // Reset state after awarding points
            isAtTop = false
            isAtBottom = false
            readyForNextRep = false
        }
    }

    /**
     * Update overlay text with animation
     */
    private fun updateOverlayText(message: String, colorResId: Int) {
        context?.let {
            pushUpOverlayText.visibility = View.VISIBLE
            pushUpOverlayText.text = message
            pushUpOverlayText.setTextColor(ContextCompat.getColor(it, colorResId))

            // Apply scale animation
            pushUpOverlayText.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(100)
                .withEndAction {
                    pushUpOverlayText.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    /**
     * Animate the push-up counter for better visual feedback
     */
    private fun animateCounterUpdate(newCount: Int) {
        context?.let {
            val message = "Push-Ups: $newCount (Streak: $consecutiveReps)"
            pushUpOverlayText.text = message
            pushUpOverlayText.setTextColor(ContextCompat.getColor(it, android.R.color.holo_green_light))

            // Create counting animation
            ValueAnimator.ofFloat(0.8f, 1.2f, 1.0f).apply {
                duration = 500
                addUpdateListener { animation ->
                    pushUpOverlayText.scaleX = animation.animatedValue as Float
                    pushUpOverlayText.scaleY = animation.animatedValue as Float
                }
                start()
            }
        }
    }

    /**
     * Check for achievements and display appropriate feedback
     */
    private fun checkForAchievements() {
        val achievements = mutableListOf<String>()

        // Rep count achievements
        when (pushUpCount) {
            10 -> achievements.add("First 10 Push-ups! 🎉")
            25 -> achievements.add("Quarter-Century: 25 Push-ups! 💪")
            50 -> achievements.add("Half-Century: 50 Push-ups! 🔥")
            100 -> achievements.add("CENTURY! 100 Push-ups! 🏆")
        }

        // Streak achievements
        when (consecutiveReps) {
            5 -> achievements.add("5 Push-ups in a row! 🔥")
            10 -> achievements.add("10 consecutive Push-ups! 🔥🔥")
            20 -> achievements.add("BEAST MODE: 20 consecutive! 🔥🔥🔥")
        }

        // Display achievements
        if (achievements.isNotEmpty()) {
            showAchievementToast(achievements.joinToString("\n"))
            logAchievements(achievements)
        }
    }

    /**
     * Display customized achievement toast
     */
    private fun showAchievementToast(message: String) {
        context?.let { ctx ->
            Handler(Looper.getMainLooper()).post {
                try {
                    // Create custom toast (simplified without custom layout reference)
                    val toast = Toast.makeText(ctx, message, Toast.LENGTH_LONG)
                    toast.show()

                    // Play achievement sound if available
                    // Simplified for implementation without audio references
                    // MediaPlayer.create(ctx, R.raw.achievement_sound)?.start()
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing achievement toast: ${e.message}")
                }
            }
        }
    }

    /**
     * Log achievements to analytics
     */
    private fun logAchievements(achievements: List<String>) {
        try {



            // Save to user's achievements in database
            val achievementRef = userRef.child("achievements").push()
            val achievementData = HashMap<String, Any>().apply {
                put("title", achievements.first())
                put("timestamp", ServerValue.TIMESTAMP)
                put("exercise", "pushup")
                put("count", pushUpCount)
            }
            achievementRef.setValue(achievementData)
        } catch (e: Exception) {
            Log.e(TAG, "Error logging achievement: ${e.message}")
        }
    }

    /**
     * Award points for completing push-ups with optimized database operations
     */
    private fun awardPushUpPoints(pointsEarned: Int) {

        if (userId.isEmpty()) {
            Log.e(TAG, "Cannot award points: userId is empty or authentication failed")
            return
        }

        try {
            // Use batch updates for efficiency
            val batchUpdates = HashMap<String, Any>()
            batchUpdates["exercises/pushups/count"] = ServerValue.increment(1)
            batchUpdates["exercises/pushups/points"] = ServerValue.increment(pointsEarned.toLong())
            batchUpdates["exercises/pushups/bestStreak"] = ServerValue.increment(0) // Will be updated by transaction
            batchUpdates["totalPoints"] = ServerValue.increment(pointsEarned.toLong())
            batchUpdates["analytics/totalCaloriesBurned"] = ServerValue.increment(CALORIES_PER_PUSHUP)

            // Apply batch updates
            userRef.updateChildren(batchUpdates)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update push-up stats: ${e.message}")
                    scheduleRetry("batch_updates", batchUpdates)
                }

            // NEW CODE: Update global leaderboard
            val currentUser = FirebaseAuth.getInstance().currentUser
            val displayName = currentUser?.displayName ?: "Anonymous"
            val leaderboardRef = database.getReference("leaderboard").child(userId)

            val leaderboardUpdates = HashMap<String, Any>()
            leaderboardUpdates["userName"] = displayName
            leaderboardUpdates["totalPoints"] = ServerValue.increment(pointsEarned.toLong())
            leaderboardUpdates["pushUpPoints"] = ServerValue.increment(pointsEarned.toLong())

            // Apply leaderboard updates
            leaderboardRef.updateChildren(leaderboardUpdates)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update leaderboard: ${e.message}")
                    scheduleRetry("leaderboard_updates", leaderboardUpdates)
                }
            // END NEW CODE

            // Update best streak with transaction
            pushUpRef.child("bestStreak").runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val currentBest = mutableData.getValue(Int::class.java) ?: 0
                    if (maxConsecutiveReps > currentBest) {
                        mutableData.value = maxConsecutiveReps
                    }
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (error != null) {
                        Log.e(TAG, "Failed to update best streak: ${error.message}")
                    } else if (committed && currentData != null) {
                        val newBest = currentData.getValue(Int::class.java) ?: 0
                        if (newBest == maxConsecutiveReps && maxConsecutiveReps > 5) {
                            showAchievementToast("New personal best streak: $maxConsecutiveReps push-ups! 🏆")
                        }
                    }
                }
            })

            // Update strength attribute with transaction
            strengthRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val strength = mutableData.getValue(Float::class.java) ?: 10f
                    mutableData.value = strength + strengthIncrementPerPushUp
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (error != null) {
                        Log.e(TAG, "Failed to update strength: ${error.message}")
                        retryOnTransactionFailure("strength", strengthIncrementPerPushUp)
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error awarding points: ${e.message}")
        }
    }


    /**
     * Retry a failed transaction with exponential backoff
     */
    private fun retryOnTransactionFailure(attribute: String, value: Float, attempt: Int = 0) {
        if (attempt >= DATABASE_RETRY_ATTEMPTS) {
            Log.e(TAG, "Giving up on updating $attribute after $DATABASE_RETRY_ATTEMPTS attempts")
            saveFailedOperationToLocalStorage(attribute, value)
            return
        }

        // Exponential backoff with jitter
        val baseDelay = Math.pow(2.0, attempt.toDouble()) * 100
        val jitter = Random().nextInt(50)
        val delayMillis = (baseDelay + jitter).toLong()

        Handler(Looper.getMainLooper()).postDelayed({
            when (attribute) {
                "strength" -> strengthRef.get().addOnSuccessListener { snapshot ->
                    val currentValue = snapshot.getValue(Float::class.java) ?: 10f
                    strengthRef.setValue(currentValue + value)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Retry failed: ${e.message}")
                    retryOnTransactionFailure(attribute, value, attempt + 1)
                }
            }
        }, delayMillis)
    }

    /**
     * Schedule retry for batch updates
     */
    private fun scheduleRetry(operationType: String, updates: Map<String, Any>, attempt: Int = 0) {
        if (attempt >= DATABASE_RETRY_ATTEMPTS) {
            Log.e(TAG, "Giving up on batch update after $DATABASE_RETRY_ATTEMPTS attempts")
            return
        }

        val delayMillis = (Math.pow(2.0, attempt.toDouble()) * 100).toLong()

        Handler(Looper.getMainLooper()).postDelayed({
            userRef.updateChildren(updates)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Batch update retry failed: ${e.message}")
                    scheduleRetry(operationType, updates, attempt + 1)
                }
        }, delayMillis)
    }

    /**
     * Store failed operations for later sync
     */
    private fun saveFailedOperationToLocalStorage(attribute: String, value: Float) {
        try {
            context?.let { ctx ->
                val prefs = ctx.getSharedPreferences("pending_operations", Context.MODE_PRIVATE)
                val pendingOps = prefs.getString("pending_ops", "[]")
                val opsArray = JSONArray(pendingOps)

                val newOp = JSONObject().apply {
                    put("type", attribute)
                    put("value", value)
                    put("timestamp", System.currentTimeMillis())
                    put("userId", userId)
                }

                opsArray.put(newOp)
                prefs.edit().putString("pending_ops", opsArray.toString()).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving operation to local storage: ${e.message}")
        }
    }

    /**
     * Save completed push-up session with enhanced analytics
     */
    private fun savePushUpSession() {
        if (userId.isEmpty() || pushUpCount == 0) return

        val sessionRef = userRef.child("exerciseHistory")
            .child("pushups")
            .push()

        val session = HashMap<String, Any>()
        session["timestamp"] = ServerValue.TIMESTAMP
        session["count"] = pushUpCount
        session["pointsEarned"] = pushUpCount * pointsPerPushUp
        session["maxConsecutiveReps"] = maxConsecutiveReps
        session["caloriesBurned"] = caloriesBurned
        session["durationSeconds"] = (System.currentTimeMillis() - sessionStartTime) / 1000

        // Add date components for easier filtering/querying
        val calendar = Calendar.getInstance()
        session["year"] = calendar.get(Calendar.YEAR)
        session["month"] = calendar.get(Calendar.MONTH) + 1
        session["day"] = calendar.get(Calendar.DAY_OF_MONTH)
        session["dayOfWeek"] = calendar.get(Calendar.DAY_OF_WEEK)
        session["hourOfDay"] = calendar.get(Calendar.HOUR_OF_DAY)

        sessionRef.setValue(session)
            .addOnSuccessListener {
                Log.d(TAG, "Push-up session saved successfully")

                // Update workout streak
                updateWorkoutStreak()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save push-up session: ${e.message}")
            }
    }

    /**
     * Update user's workout streak
     */
    private fun updateWorkoutStreak() {
        analyticsRef.child("lastWorkout").get().addOnSuccessListener { snapshot ->
            val lastWorkoutTimestamp = snapshot.getValue(Long::class.java) ?: 0
            val currentDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // Get previous date
            val previousDate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // If last workout was before today
            if (lastWorkoutTimestamp < currentDate) {
                // Update last workout timestamp
                analyticsRef.child("lastWorkout").setValue(ServerValue.TIMESTAMP)

                // If streak continues or this is first workout
                if (lastWorkoutTimestamp >= previousDate || lastWorkoutTimestamp.toInt() == 0) {
                    // Increment streak
                    analyticsRef.child("workoutStreak").runTransaction(object : Transaction.Handler {
                        override fun doTransaction(mutableData: MutableData): Transaction.Result {
                            val currentStreak = mutableData.getValue(Int::class.java) ?: 0
                            mutableData.value = currentStreak + 1
                            return Transaction.success(mutableData)
                        }

                        override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                            if (error == null && committed && currentData != null) {
                                val newStreak = currentData.getValue(Int::class.java) ?: 0
                                if (newStreak > 1) {
                                    showAchievementToast("Workout Streak: $newStreak days! 🔥")
                                }
                            }
                        }
                    })
                } else {
                    // Streak broken, reset to 1
                    analyticsRef.child("workoutStreak").setValue(1)
                }
            }
        }
    }



    /**
     * Reset the push-up counter and state with animation
     */
    fun reset() {
        // Save current session if there are any push-ups
        if (pushUpCount > 0) {
            savePushUpSession()
        }

        // Reset state variables
        isAtTop = false
        isAtBottom = false
        pushUpCount = 0
        consecutiveReps = 0
        maxConsecutiveReps = 0
        caloriesBurned = 0.0
        sessionStartTime = System.currentTimeMillis()

        // Clear and reset UI
        context?.let {
            pushUpOverlayText.text = "Push-up tracker reset"
            pushUpOverlayText.setTextColor(ContextCompat.getColor(it, android.R.color.white))
            pushUpOverlayText.visibility = View.VISIBLE

            // Fade out after 2 seconds
            pushUpOverlayText.animate()
                .alpha(0f)
                .setStartDelay(2000)
                .setDuration(500)
                .withEndAction {
                    pushUpOverlayText.visibility = View.GONE
                    pushUpOverlayText.alpha = 1f
                }
                .start()
        }

        Log.d(TAG, "Push-up tracker reset")
    }

    /**
     * Clean up session and sync data
     */
    fun cleanup() {
        if (pushUpCount > 0) {
            savePushUpSession()
        }

        // Sync any pending operations
        syncPendingOperations()

        pushUpOverlayText.visibility = View.GONE
    }

    /**
     * Sync any pending operations stored locally
     */
    private fun syncPendingOperations() {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("pending_operations", Context.MODE_PRIVATE)
                val pendingOps = prefs.getString("pending_ops", "[]")
                val opsArray = JSONArray(pendingOps)

                if (opsArray.length() > 0) {
                    Log.d(TAG, "Syncing ${opsArray.length()} pending operations")

                    for (i in 0 until opsArray.length()) {
                        val op = opsArray.getJSONObject(i)
                        val type = op.getString("type")
                        val value = op.getDouble("value").toFloat()
                        val opUserId = op.getString("userId")

                        // Only process operations for current user
                        if (opUserId == userId) {
                            when (type) {
                                "strength" -> {
                                    strengthRef.setValue(ServerValue.increment(value.toDouble()))
                                }
                            }
                        }
                    }

                    // Clear processed operations
                    prefs.edit().putString("pending_ops", "[]").apply()
                } else {

                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing pending operations: ${e.message}")
            }
        }
    }


}
