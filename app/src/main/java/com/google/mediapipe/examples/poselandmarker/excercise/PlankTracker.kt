package com.google.mediapipe.examples.poselandmarker.exercise

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.mediapipe.examples.poselandmarker.achievements.AchievementConstants
import com.google.mediapipe.examples.poselandmarker.achievements.AchievementManager
import com.google.mediapipe.examples.poselandmarker.excercise.AngleHelper
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * PlankTracker: Detects plank exercise position and awards points according to
 * the gamification system described in the technical documentation.
 *
 * Points are awarded every 2 seconds while maintaining proper plank form,
 * with updates to stamina attributes in the player profile.
 */
class PlankTracker(
    private var userId: String,
    private val database: FirebaseDatabase,
    private val plankOverlayText: TextView,
    private val context: android.content.Context? = null
) {
    // State tracking
    private var isInPlankPosition = false
    private var plankStartTime = 0L
    private var lastPointAwardTime = 0L
    var totalPlankDuration = 0L

    // Configuration
    private val pointAwardInterval = 2000 // 2 seconds in milliseconds
    private val pointsPerInterval = 1 // Points awarded every 2 seconds
    private val staminaIncrementPerInterval = 0.2f // Stamina increase per 2 seconds
    private val angleHelper = AngleHelper()

    // Database references
    private lateinit var userRef: DatabaseReference
    private lateinit var plankRef: DatabaseReference
    private lateinit var staminaRef: DatabaseReference

    // Stats tracking
    private var sessionPointsEarned = 0
    private var sessionSeconds = 0

    companion object {
        private const val TAG = "PlankTracker"
        private const val MIN_PLANK_DURATION = 3 // Minimum seconds for a valid plank
        private const val DATABASE_RETRY_ATTEMPTS = 3
    }

    init {
        if (userId.isNotEmpty()) {
            initializeDatabaseReferences()
            verifyDatabaseStructure()
        }
    }
    val plankRef1 = FirebaseDatabase.getInstance().getReference("users").child(userId).child("plank")

    /**
     * Initialize Firebase database references for frequently accessed paths
     */
    private fun initializeDatabaseReferences() {
        userRef = database.reference.child("users").child(userId)
        plankRef = userRef.child("exercises").child("plank")
        staminaRef = userRef.child("attributes").child("stamina")
    }

    /**
     * Verify and create necessary database structure for new users
     */
    private fun verifyDatabaseStructure() {
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.hasChild("attributes") ||
                    !snapshot.hasChild("exercises") ||
                    !snapshot.child("exercises").hasChild("plank")) {

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

                    // Initialize exercises/plank if missing
                    if (!snapshot.hasChild("exercises") ||
                        !snapshot.child("exercises").hasChild("plank")) {
                        val plank = HashMap<String, Any>()
                        plank["points"] = 0
                        plank["totalSeconds"] = 0
                        plank["bestDuration"] = 0L
                        updates["exercises/plank"] = plank
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
                Log.e(TAG, "Database structure verification failed: ${error.message}")
            }
        })
    }

    /**
     * Process landmarks from MediaPipe pose detection to identify plank position
     */
    fun processLandmarks(landmarks: List<NormalizedLandmark>) {
        if (landmarks.size < 29) return

        try {
            // Extract required landmarks for plank detection
            val leftShoulder = AngleHelper.PointF(landmarks[11].x(), landmarks[11].y())
            val rightShoulder = AngleHelper.PointF(landmarks[12].x(), landmarks[12].y())
            val leftHip = AngleHelper.PointF(landmarks[23].x(), landmarks[23].y())
            val rightHip = AngleHelper.PointF(landmarks[24].x(), landmarks[24].y())
            val leftAnkle = AngleHelper.PointF(landmarks[27].x(), landmarks[27].y())
            val rightAnkle = AngleHelper.PointF(landmarks[28].x(), landmarks[28].y())
            val leftElbow = AngleHelper.PointF(landmarks[13].x(), landmarks[13].y())
            val rightElbow = AngleHelper.PointF(landmarks[14].x(), landmarks[14].y())
            val leftWrist = AngleHelper.PointF(landmarks[15].x(), landmarks[15].y())
            val rightWrist = AngleHelper.PointF(landmarks[16].x(), landmarks[16].y())


            // Detect if user is in plank position
            val currentPlankState = angleHelper.isPlank(
                leftShoulder, rightShoulder,
                leftHip, rightHip,
                leftAnkle, rightAnkle,
                leftElbow, rightElbow,
                leftWrist, rightWrist
            )


            // Get current time for calculations
            val currentTime = System.currentTimeMillis()

            handlePlankState(currentPlankState, currentTime)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing landmarks: ${e.message}")
        }
    }

    /**
     * Handle transitions between plank states and time-based events
     */
    private fun handlePlankState(isInPlank: Boolean, currentTime: Long) {
        when {
            // Just entered plank position
            isInPlank && !isInPlankPosition -> {
                startPlank(currentTime)
            }

            // Continuing plank position
            isInPlank && isInPlankPosition -> {
                continuePlank(currentTime)
            }

            // Just exited plank position
            !isInPlank && isInPlankPosition -> {
                endPlank(currentTime)
            }

            // Not in plank position
            else -> {
                plankOverlayText.visibility = View.GONE
            }
        }
    }

    /**
     * Start tracking a new plank exercise
     */
    /**
     * Start tracking a new plank exercise with enhanced feedback
     */
    private fun startPlank(currentTime: Long) {
        isInPlankPosition = true
        plankStartTime = currentTime
        lastPointAwardTime = currentTime
        sessionPointsEarned = 0
        sessionSeconds = 0
        totalPlankDuration = 0L

        // Show plank detected indicator with enhanced animation
        plankOverlayText.apply {
            alpha = 0f
            visibility = View.VISIBLE
            text = "Plank detected - hold position!"
            setTextColor(ContextCompat.getColor(context ?: return, android.R.color.holo_red_light))

            // Combined fade-in and scale animation
            animate()
                .alpha(1f)
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(300)
                .withEndAction {
                    animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }
        Log.d(TAG, "Plank position started")
    }


    /**
     * Update tracking for continuing plank position
     */
    /**
     * Update tracking for continuing plank position with enhanced visual feedback
     * and performance optimizations
     */
    private fun continuePlank(currentTime: Long) {
        // Always update duration for UI responsiveness
        totalPlankDuration = currentTime - plankStartTime
        val totalDurationSec = totalPlankDuration / 1000

        // Format time as mm:ss for longer durations
        val timeDisplay = if (totalDurationSec >= 60) {
            val minutes = totalDurationSec / 60
            val seconds = totalDurationSec % 60
            String.format("%d:%02d", minutes, seconds)
        } else {
            "${totalDurationSec}s"
        }

        // Check if it's time to award points
        if (currentTime - lastPointAwardTime >= pointAwardInterval) {
            // Calculate intervals completed
            val intervalsSinceLastAward = (currentTime - lastPointAwardTime) / pointAwardInterval

            // Keep track of previous seconds for milestone detection
            val previousSeconds = sessionSeconds

            // Award points for each interval
            for (i in 0 until intervalsSinceLastAward) {
                awardPlankPoints()
                sessionSeconds += 2
                sessionPointsEarned += pointsPerInterval
            }

            // Update last award time
            lastPointAwardTime += intervalsSinceLastAward * pointAwardInterval

            // Update UI text with formatted time and points
            plankOverlayText.text = "Plank: $timeDisplay (+$pointsPerInterval pts)"

            // Check for milestone achievements
            checkForMilestones(previousSeconds, sessionSeconds)
        }

        // Update UI color based on duration - only when needed
        updateUIColor(totalDurationSec)
    }

    /**
     * Update UI color based on plank duration
     */
    private fun updateUIColor(durationSeconds: Long) {
        context?.let { ctx ->
            val newColor = when {
                durationSeconds >= 120 -> android.R.color.holo_purple      // Master: 2min+
                durationSeconds >= 60 -> android.R.color.holo_green_light  // Expert: 1min+
                durationSeconds >= 30 -> android.R.color.holo_blue_light   // Intermediate: 30sec+
                durationSeconds >= 15 -> android.R.color.holo_orange_light // Beginner: 15sec+
                else -> android.R.color.holo_red_light                     // Starting
            }

            val currentColor = plankOverlayText.currentTextColor
            val targetColor = ContextCompat.getColor(ctx, newColor)

            // Only update if color has changed
            if (currentColor != targetColor) {
                // Animate color transition
                plankOverlayText.animate()
                    .setDuration(300)
                    .withEndAction {
                        plankOverlayText.setTextColor(targetColor)
                    }
                    .start()
            }
        }
    }

    /**
     * Check for plank duration milestones and provide feedback
     */
    private fun checkForMilestones(previousSeconds: Int, currentSeconds: Int) {
        // Define milestones in seconds
        val milestones = listOf(15, 30, 60, 120)

        // Check if we've crossed any milestone
        for (milestone in milestones) {
            if (previousSeconds < milestone && currentSeconds >= milestone) {
                // We've just reached this milestone!
                provideMilestoneFeedback(milestone)
                break
            }
        }
    }

    /**
     * Provide visual and optional audio feedback when reaching milestones
     */
    private fun provideMilestoneFeedback(seconds: Int) {
        context?.let { ctx ->
            // Pulse animation for text
            plankOverlayText.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(200)
                .withEndAction {
                    plankOverlayText.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()
                }
                .start()

            // Optional toast message for significant milestones
            if (seconds >= 30) {
                val message = when(seconds) {
                    30 -> "Great job! 30 second milestone reached!"
                    60 -> "Excellent! You've held the plank for 1 minute!"
                    120 -> "Incredible! 2 minute plank achieved!"
                    else -> null
                }

                message?.let {
                    Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    /**
     * End the current plank exercise session
     */
    /**
     * End the current plank exercise session with improved feedback
     */
    private fun endPlank(currentTime: Long) {
        isInPlankPosition = false
        totalPlankDuration = currentTime - plankStartTime
        val durationSeconds = totalPlankDuration / 1000

        // Final UI update before fadeout
        val timeDisplay = formatDuration(durationSeconds)
        plankOverlayText.text = "Plank completed: $timeDisplay"

        // Enhanced fade-out animation with scale down
        plankOverlayText.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(500)
            .withEndAction {
                plankOverlayText.visibility = View.GONE
                plankOverlayText.scaleX = 1.0f
                plankOverlayText.scaleY = 1.0f
            }
            .start()

        Log.d(TAG, "Plank ended. Total duration: ${durationSeconds} seconds")

        // Save the plank session to history in Firebase
        savePlankSession(totalPlankDuration)

        // Show achievement message for longer planks with adaptive design
        if (durationSeconds >= 15 && context != null) {
            showPlankAchievement(durationSeconds)
        }
    }

    /**
     * Format duration in seconds to readable format
     */
    private fun formatDuration(seconds: Long): String {
        return if (seconds >= 60) {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            String.format("%d:%02d", minutes, remainingSeconds)
        } else {
            "${seconds}s"
        }
    }

    /**
     * Show achievement toast with enhanced UI
     */
    private fun showPlankAchievement(durationSeconds: Long) {
        context?.let { ctx ->
            val achievementLevel = when {
                durationSeconds >= 180 -> "LEGENDARY"
                durationSeconds >= 120 -> "MASTER"
                durationSeconds >= 60 -> "EXPERT"
                durationSeconds >= 30 -> "INTERMEDIATE"
                else -> "BEGINNER"
            }

            val message = "🔥 $achievementLevel PLANK: $durationSeconds seconds! 🔥"

            // Create custom toast for achievement
            try {
                val inflater = LayoutInflater.from(ctx)
                // This assumes you have a custom toast layout - if not, use standard Toast
                val customToast = Toast.makeText(ctx, message, Toast.LENGTH_LONG)
                customToast.setGravity(Gravity.CENTER, 0, 0)
                customToast.show()
            } catch (e: Exception) {
                // Fallback to regular toast if custom view fails
                Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
            }
        }
    }


    /**
     * Award points for maintaining plank position
     * Called every 2 seconds while in plank position
     */
    /**
     * Award points for maintaining plank position using batched operations
     * for better efficiency and reliability
     */
    private fun awardPlankPoints() {
        if (userId.isEmpty()) {
            Log.e(TAG, "Cannot award points: userId is empty or authentication failed")
            return
        }

        try {
            // Use batch update for non-critical fields
            val batchUpdates = HashMap<String, Any>()
            batchUpdates["exercises/plank/points"] = ServerValue.increment(pointsPerInterval.toLong())
            batchUpdates["exercises/plank/totalSeconds"] = ServerValue.increment(2)
            batchUpdates["totalPoints"] = ServerValue.increment(pointsPerInterval.toLong())

            // Apply batch updates
            userRef.updateChildren(batchUpdates)
                .addOnSuccessListener {
                    Log.d(TAG, "Batch update successful: +$pointsPerInterval points, +2 seconds")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Batch update failed: ${e.message}")
                    scheduleRetry("batch_update", batchUpdates)
                }

            // NEW CODE: Update global leaderboard
            val currentUser = FirebaseAuth.getInstance().currentUser
            val displayName = currentUser?.displayName ?: "Anonymous"
            val leaderboardRef = database.getReference("leaderboard").child(userId)

            val leaderboardUpdates = HashMap<String, Any>()
            leaderboardUpdates["userName"] = displayName
            leaderboardUpdates["totalPoints"] = ServerValue.increment(pointsPerInterval.toLong())
            leaderboardUpdates["plankPoints"] = ServerValue.increment(pointsPerInterval.toLong())

            // Apply leaderboard updates
            leaderboardRef.updateChildren(leaderboardUpdates)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to update leaderboard: ${e.message}")
                    scheduleRetry("leaderboard_updates", leaderboardUpdates)
                }
            // END NEW CODE

            // Stamina still uses transaction for atomicity
            staminaRef.runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val stamina = mutableData.getValue(Float::class.java) ?: 10f
                    mutableData.value = stamina + staminaIncrementPerInterval
                    return Transaction.success(mutableData)
                }

                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (error != null) {
                        Log.e(TAG, "Failed to update stamina: ${error.message}")
                        retryOnTransactionFailure("stamina", staminaIncrementPerInterval)
                    } else {
                        Log.d(TAG, "Stamina updated to: ${currentData?.getValue(Float::class.java)}")
                    }
                }
            })

            Log.d(TAG, "Awarded $pointsPerInterval points for plank")
        } catch (e: Exception) {
            Log.e(TAG, "Error in awardPlankPoints: ${e.message}")
        }
    }



    /**
     * Retry a failed transaction with exponential backoff
     */
    private fun retryOnTransactionFailure(attribute: String, value: Float, attempt: Int = 0) {
        if (attempt >= DATABASE_RETRY_ATTEMPTS) {
            Log.e(TAG, "Giving up on updating $attribute after $DATABASE_RETRY_ATTEMPTS attempts")
            return
        }

        // Exponential backoff delay
        val delayMillis = (Math.pow(2.0, attempt.toDouble()) * 100).toLong()

        Timer().schedule(object : TimerTask() {
            override fun run() {
                Log.d(TAG, "Retrying $attribute update, attempt ${attempt + 1}")

                // Simplified direct update instead of transaction for retry
                when (attribute) {
                    "stamina" -> staminaRef.get().addOnSuccessListener { snapshot ->
                        val currentValue = snapshot.getValue(Float::class.java) ?: 10f
                        staminaRef.setValue(currentValue + value)
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Retry failed: ${e.message}")
                        retryOnTransactionFailure(attribute, value, attempt + 1)
                    }
                    // Add other attributes if needed
                }
            }
        }, delayMillis)
    }

    /**
     * Save completed plank session to exercise history
     */
    private fun savePlankSession(durationMillis: Long) {
        if (userId.isEmpty()) return

        val durationSeconds = durationMillis / 1000

        // Skip if duration too short
        if (durationSeconds < MIN_PLANK_DURATION) return

        // Create session entry with comprehensive metadata
        val sessionRef = userRef.child("exerciseHistory")
            .child("plank")
            .push() // Creates unique ID

        val session = HashMap<String, Any>()
        session["timestamp"] = ServerValue.TIMESTAMP
        session["durationSeconds"] = durationSeconds
        session["pointsEarned"] = sessionPointsEarned

        // Add date components for easier filtering/querying
        val calendar = Calendar.getInstance()
        session["year"] = calendar.get(Calendar.YEAR)
        session["month"] = calendar.get(Calendar.MONTH) + 1
        session["day"] = calendar.get(Calendar.DAY_OF_MONTH)

        sessionRef.setValue(session)
            .addOnSuccessListener {
                Log.d(TAG, "Plank session saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save plank session: ${e.message}")
            }

        // Update best duration if this is longer
        plankRef.child("bestDuration").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val best = mutableData.getValue(Long::class.java) ?: 0L
                mutableData.value = if (durationSeconds > best) durationSeconds else best
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Log.e(TAG, "Failed to update best duration: ${error.message}")
                } else if (committed && currentData != null) {
                    val newBest = currentData.getValue(Long::class.java)
                    if (newBest == durationSeconds) {
                        Log.d(TAG, "New personal best: $durationSeconds seconds!")
                        // Could trigger achievement notification here
                    }
                }
            }
        })
    }

    /**
     * Clean up any ongoing plank session
     * Should be called in onPause() of the host fragment/activity
     */
    fun cleanup() {
        if (isInPlankPosition) {
            val totalTime = System.currentTimeMillis() - plankStartTime
            savePlankSession(totalTime)
            isInPlankPosition = false
        }
    }

    /**
     * Schedule retry for batch updates with exponential backoff and jitter
     */
    private fun scheduleRetry(operationType: String, updates: Map<String, Any>, attempt: Int = 0) {
        if (attempt >= DATABASE_RETRY_ATTEMPTS) {
            Log.e(TAG, "Giving up on $operationType after $DATABASE_RETRY_ATTEMPTS attempts")
            saveFailedOperationToLocalStorage(operationType, updates)
            return
        }

        // Add jitter to avoid thundering herd problem
        val baseDelay = Math.pow(2.0, attempt.toDouble()) * 100
        val jitter = Random().nextInt(50)
        val delayMillis = (baseDelay + jitter).toLong()

        Handler(Looper.getMainLooper()).postDelayed({
            Log.d(TAG, "Retrying $operationType, attempt ${attempt + 1}")

            userRef.updateChildren(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Retry successful for $operationType")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Retry failed for $operationType: ${e.message}")
                    scheduleRetry(operationType, updates, attempt + 1)
                }
        }, delayMillis)
    }

    /**
     * Save failed operations for offline support
     */
    private fun saveFailedOperationToLocalStorage(operationType: String, updates: Map<String, Any>) {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("plank_offline_operations", Context.MODE_PRIVATE)
                val existingData = prefs.getString("pending_operations", "[]")
                val pendingOps = JSONArray(existingData)

                val operation = JSONObject().apply {
                    put("type", operationType)
                    put("userId", userId)
                    put("timestamp", System.currentTimeMillis())

                    val updatesJson = JSONObject()
                    for ((key, value) in updates) {
                        updatesJson.put(key, value.toString())
                    }
                    put("updates", updatesJson)
                }

                pendingOps.put(operation)
                prefs.edit().putString("pending_operations", pendingOps.toString()).apply()

                Log.d(TAG, "Saved operation to local storage for later sync")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save operation to local storage: ${e.message}")
            }
        }
    }

    fun checkForAchievements() {
        val achievementManager = AchievementManager(database)

        // Check plank duration achievements
        for ((id, info) in AchievementConstants.PLANK_ACHIEVEMENTS) {
            val durationSeconds = totalPlankDuration / 1000
            if (durationSeconds >= info.threshold) {
                achievementManager.unlockAchievement(userId, id, mapOf(
                    "title" to info.title,
                    "description" to info.description,
                    "exerciseType" to "plank",
                    "value" to durationSeconds
                ))
                //showAchievementToast(info.title)
            }
        }
    }




}
