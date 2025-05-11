package com.google.mediapipe.examples.poselandmarker.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import com.google.mediapipe.examples.poselandmarker.MainViewModel
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.challenges.DailyChallenge
import com.google.mediapipe.examples.poselandmarker.challenges.DailyChallengeManager
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.examples.poselandmarker.exercise.PlankTracker
import com.google.mediapipe.examples.poselandmarker.exercise.PushUpHelper
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min



class CameraFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {

    companion object {
        private const val TAG = "Pose Landmarker"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    // Use this to control which camera is active
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    // Reference to Firebase
    private lateinit var database: FirebaseDatabase
    private lateinit var userId: String
    private lateinit var plankTracker: PlankTracker
    private lateinit var pushUpHelper: PushUpHelper

    // Daily Challenges
    private lateinit var challengeBadge: CardView
    private lateinit var challengeCard: CardView
    private lateinit var challengeManager: DailyChallengeManager
    private var currentChallenge: DailyChallenge? = null

    // Track if camera preview is visible
    var isCameraPreviewVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()
        userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }
        backgroundExecutor.execute {
            if(this::poseLandmarkerHelper.isInitialized) {
                if (poseLandmarkerHelper.isClose()) {
                    poseLandmarkerHelper.setupPoseLandmarker()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::plankTracker.isInitialized) {
            plankTracker.cleanup()
        }
        if (::pushUpHelper.isInitialized) {
            pushUpHelper.cleanup()
        }
        if(this::poseLandmarkerHelper.isInitialized) {
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    var challengeFeatureEnabled = true // Set to true when ready to enable
    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Camera switch button setup
        val cameraSwitchButton: FloatingActionButton = view.findViewById(R.id.camera_switch_button)
        cameraSwitchButton.setOnClickListener {
            toggleCamera()
        }

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Initialize plank tracker
        plankTracker = PlankTracker(
            userId = userId,
            database = database,
            plankOverlayText = fragmentCameraBinding.plankOverlayText
        )

        // Initialize push-up helper
        pushUpHelper = PushUpHelper(
            userId = userId,
            database = database,
            pushUpOverlayText = fragmentCameraBinding.pushUpOverlayText,
            context = requireContext()
        )

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            setUpCamera()
        }

        backgroundExecutor.execute {
            poseLandmarkerHelper = PoseLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                currentDelegate = viewModel.currentDelegate,
                poseLandmarkerHelperListener = this
            )
        }

        initBottomSheetControls()

        // Initialize challenge components
        challengeBadge = view.findViewById(R.id.challengeBadge)
        challengeCard = view.findViewById(R.id.challengeCard)
        challengeManager = DailyChallengeManager(FirebaseDatabase.getInstance())

        // Hide challenge card initially
        challengeCard.visibility = View.GONE

        // Set up badge click behavior
        challengeBadge.setOnClickListener {
            toggleChallengeCard()
        }

        // Set up dismiss button
        view.findViewById<Button>(R.id.dismissButton).setOnClickListener {
            challengeCard.visibility = View.GONE
        }

        // Load current challenge
        if (challengeFeatureEnabled) {
            Log.e("ChallengeFeature", "Clicked: ")
            // Only try to find these views if the feature is enabled
            try {
                challengeBadge = view.findViewById(R.id.challengeBadge)
                challengeCard = view.findViewById(R.id.challengeCard)
                challengeManager = DailyChallengeManager(FirebaseDatabase.getInstance())

                // Set up badge click behavior
                challengeBadge.setOnClickListener {
                    toggleChallengeCard()
                }

                // Load current challenge
                loadCurrentChallenge()
            } catch (e: Exception) {
                Log.e("ChallengeFeature", "Error initializing challenge system: ${e.message}")
            }
        }
    }

    private fun initBottomSheetControls() {
        // init bottom sheet settings
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence
            )

        // When clicked, lower pose detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseDetectionConfidence >= 0.2) {
                poseLandmarkerHelper.minPoseDetectionConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise pose detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseDetectionConfidence <= 0.8) {
                poseLandmarkerHelper.minPoseDetectionConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower pose tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseTrackingConfidence >= 0.2) {
                poseLandmarkerHelper.minPoseTrackingConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise pose tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPoseTrackingConfidence <= 0.8) {
                poseLandmarkerHelper.minPoseTrackingConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower pose presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (poseLandmarkerHelper.minPosePresenceConfidence >= 0.2) {
                poseLandmarkerHelper.minPosePresenceConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise pose presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (poseLandmarkerHelper.minPosePresenceConfidence <= 0.8) {
                poseLandmarkerHelper.minPosePresenceConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference.
        // Current options are CPU and GPU
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                ) {
                    try {
                        poseLandmarkerHelper.currentDelegate = p2
                        updateControlsUi()
                    } catch(e: UninitializedPropertyAccessException) {
                        Log.e(TAG, "PoseLandmarkerHelper has not been initialized yet.")
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for object detection
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.currentModel,
            false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?,
                    p1: View?,
                    p2: Int,
                    p3: Long
                ) {
                    poseLandmarkerHelper.currentModel = p2
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset Poselandmarker
    // helper.
    private fun updateControlsUi() {
        if(this::poseLandmarkerHelper.isInitialized) {
            fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
                String.format(
                    Locale.US,
                    "%.2f",
                    poseLandmarkerHelper.minPoseDetectionConfidence
                )
            fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
                String.format(
                    Locale.US,
                    "%.2f",
                    poseLandmarkerHelper.minPoseTrackingConfidence
                )
            fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
                String.format(
                    Locale.US,
                    "%.2f",
                    poseLandmarkerHelper.minPosePresenceConfidence
                )

            // Needs to be cleared instead of reinitialized because the GPU
            // delegate needs to be initialized on the thread using it when applicable
            backgroundExecutor.execute {
                poseLandmarkerHelper.clearPoseLandmarker()
                poseLandmarkerHelper.setupPoseLandmarker()
            }
            fragmentCameraBinding.overlay.clear()
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector = CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        imageAnalyzer =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectPose(image)
                    }
                }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectPose(imageProxy: ImageProxy) {
        if(this::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.detectLiveStream(
                imageProxy = imageProxy,
                isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        // Only update overlay if the preview is visible
        if (!isCameraPreviewVisible) return
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)

                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                fragmentCameraBinding.overlay.invalidate()

                val result = resultBundle.results.first()
                val landmarksList = result.landmarks()

                if (landmarksList.isNotEmpty() && landmarksList[0].isNotEmpty()) {
                    plankTracker.processLandmarks(landmarksList[0])
                    pushUpHelper.processLandmarks(landmarksList[0])


                    // Update challenge progress
                    updateChallengeProgress("pushup", pushUpHelper.pushUpCount)
                    updateChallengeProgress("plank", (plankTracker.totalPlankDuration / 1000).toInt())
                }
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    fun toggleCameraPreview() {
        if (fragmentCameraBinding.viewFinder.visibility == View.VISIBLE) {
            isCameraPreviewVisible = false
            backgroundExecutor.execute { poseLandmarkerHelper.clearPoseLandmarker() }
            fragmentCameraBinding.overlay.clear()
            fragmentCameraBinding.viewFinder.visibility = View.GONE
            cameraProvider?.unbindAll()
            viewModel.setMinPoseDetectionConfidence(poseLandmarkerHelper.minPoseDetectionConfidence)
            viewModel.setMinPoseTrackingConfidence(poseLandmarkerHelper.minPoseTrackingConfidence)
            viewModel.setMinPosePresenceConfidence(poseLandmarkerHelper.minPosePresenceConfidence)
            viewModel.setDelegate(poseLandmarkerHelper.currentDelegate)
        } else {
            isCameraPreviewVisible = true
            fragmentCameraBinding.viewFinder.visibility = View.VISIBLE
            setUpCamera()
            backgroundExecutor.execute {
                poseLandmarkerHelper = PoseLandmarkerHelper(
                    context = requireContext(),
                    runningMode = RunningMode.LIVE_STREAM,
                    minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                    minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                    minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                    currentDelegate = viewModel.currentDelegate,
                    poseLandmarkerHelperListener = this
                )
            }
        }
    }

    /** Camera toggle function: switches between front/back cameras **/
    private fun toggleCamera() {
        cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        // Rebind camera use cases with the new lens facing
        bindCameraUseCases()
    }

    private fun toggleChallengeCard() {

        if (challengeCard.visibility == View.VISIBLE) {
            Log.d(TAG, "Hiding challenge card")
            // Animate card hiding
            challengeCard.animate()
                .alpha(0f)
                .translationY(-50f)
                .setDuration(300)
                .withEndAction {
                    challengeCard.visibility = View.GONE
                }
                .start()
        } else if (currentChallenge != null) {
            Log.d(TAG, "Showing challenge card for: ${currentChallenge?.title}")

            // Prepare card
            view?.findViewById<TextView>(R.id.challengeTitle)?.text = currentChallenge?.title
            view?.findViewById<TextView>(R.id.challengeDescription)?.text = currentChallenge?.description
            view?.findViewById<TextView>(R.id.challengeReward)?.text = "Reward: ${currentChallenge?.pointsReward} points"

            // Reset position and show
            challengeCard.translationY = -50f
            challengeCard.alpha = 0f
            challengeCard.visibility = View.VISIBLE

            // Animate card showing
            challengeCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun loadCurrentChallenge() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val challengesRef = FirebaseDatabase.getInstance().reference
            .child("users").child(userId).child("challenges")

        challengesRef.orderByChild("completed").equalTo(false).limitToFirst(1)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (challengeSnapshot in snapshot.children) {
                            // Get the Firebase-generated ID (key)
                            val challengeId = challengeSnapshot.key ?: ""

                            // Get the challenge data
                            val challenge = challengeSnapshot.getValue(DailyChallenge::class.java)

                            if (challenge != null) {
                                // Ensure we use the Firebase key as the ID
                                currentChallenge = challenge.copy(id = challengeId)
                                Log.d(TAG, "Loaded challenge: ID=${currentChallenge?.id}")
                                pulseChallengeBadge()
                                break
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Error handling
                }
            })
    }


    private fun pulseChallengeBadge() {
        challengeBadge.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(300)
            .withEndAction {
                challengeBadge.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .start()
            }
            .start()
    }

    // Update progress when exercise is completed
    private fun updateChallengeProgress(exerciseType: String, count: Int) {
        if (!challengeFeatureEnabled || currentChallenge == null) return

        val challenge = currentChallenge ?: return

        if (challenge.exerciseType == exerciseType) {
            // Update progress visually
            val progressBar = view?.findViewById<ProgressBar>(R.id.challengeProgress)
            progressBar?.let {
                val progress = min(count * 100 / challenge.targetCount, 100)
                it.progress = progress

                // FIXED CODE - Save progress to Firebase with better error handling
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null && challenge.id.isNotEmpty()) {
                    Log.d(TAG, "Updating challenge: ${challenge.id} - Progress: $progress%")

                    val challengeRef = FirebaseDatabase.getInstance().reference
                        .child("users").child(userId).child("challenges").child(challenge.id)

                    // Use updateChildren for more atomic updates
                    val updates = HashMap<String, Any>()
                    updates["progress"] = progress

                    challengeRef.updateChildren(updates)
                        .addOnSuccessListener {
                            Log.d(TAG, "Successfully updated challenge progress")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to update: ${e.message}")
                        }
                }
            }

            // Check if challenge completed
            if (count >= challenge.targetCount) {
                completeChallenge(challenge.id)
            }
        }
    }


    private fun completeChallenge(challengeId: String) {
        challengeManager.completeChallenge(challengeId)
        showChallengeCompletedAnimation()
        awardChallengePoints(currentChallenge?.pointsReward ?: 0)
    }

    private fun showChallengeCompletedAnimation() {
        context?.let { ctx ->
            // Show toast with animation
            Toast.makeText(ctx, "Challenge Completed!", Toast.LENGTH_LONG).show()

            // Visual feedback
            challengeCard.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(300)
                .withEndAction {
                    challengeCard.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(300)
                        .start()
                }
                .start()
        }
    }

    private fun awardChallengePoints(points: Int) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(userId)

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
}


