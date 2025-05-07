
package com.google.mediapipe.examples.poselandmarker

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.mediapipe.examples.poselandmarker.databinding.ActivityMainBinding
import com.google.mediapipe.examples.poselandmarker.fragment.CameraFragment

class MainActivity : AppCompatActivity() {
    private lateinit var activityMainBinding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var auth: FirebaseAuth
    private var originalCameraText: CharSequence? = null







    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if user is logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // User is not logged in, redirect to LoginActivity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        setupNavigation()

        // Add welcome message showing authenticated user
        Toast.makeText(this, "Welcome, ${currentUser.displayName ?: "Fitness Warrior"}",
            Toast.LENGTH_SHORT).show()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as NavHostFragment
        val navController = navHostFragment.navController

        // Store original camera text for restoring later
        originalCameraText = activityMainBinding.navigation.menu.findItem(R.id.camera_fragment).title

        // Connect bottom navigation with navigation controller
        activityMainBinding.navigation.setupWithNavController(navController)

        // Unified navigation logic for returning to HomeFragment
        val returnToHome: () -> Unit = {
            val currentFragment = getCurrentFragment()
            if (currentFragment is CameraFragment) {
                if (currentFragment.isCameraPreviewVisible) {
                    currentFragment.toggleCameraPreview()
                }
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val freshNavController = (supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment)?.navController
                        if (freshNavController?.popBackStack(R.id.homeFragment, false) == true) {
                            // Success
                        } else {
                            freshNavController?.navigate(R.id.homeFragment)
                        }
                    } catch (e: Exception) {
                        Log.e("Navigation", "Navigation error: ${e.message}")
                    }
                }, 250)
            }
        }

        // Handle reselection: ensure "Return" always returns to HomeFragment
        activityMainBinding.navigation.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.camera_fragment) {
                returnToHome()
            }
        }

        // Handle item selection
        activityMainBinding.navigation.setOnItemSelectedListener { item ->
            val currentFragment = getCurrentFragment()
            when {
                item.itemId == R.id.camera_fragment && currentFragment is CameraFragment -> {
                    returnToHome()
                    true
                }
                else -> NavigationUI.onNavDestinationSelected(item, navController)
            }
        }

        // Listen for destination changes
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment -> {
                    setBottomNavigationVisibility(false)
                    activityMainBinding.navigation.menu.findItem(R.id.camera_fragment).title = originalCameraText
                }
                R.id.camera_fragment -> {
                    setBottomNavigationVisibility(true)
                    activityMainBinding.navigation.menu.findItem(R.id.camera_fragment).title = "Return"
                }
                else -> {
                    setBottomNavigationVisibility(true)
                    activityMainBinding.navigation.menu.findItem(R.id.camera_fragment).title = originalCameraText
                }
            }
        }
    }

    fun setBottomNavigationVisibility(isVisible: Boolean) {
        activityMainBinding.navigation.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun getCurrentFragment(): Fragment? {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment
        return navHostFragment?.childFragmentManager?.primaryNavigationFragment
    }

    fun logout() {
        auth.signOut()
        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    override fun onBackPressed() {
        val currentFragment = getCurrentFragment()
        if (currentFragment is CameraFragment) {
            if (currentFragment.isCameraPreviewVisible) {
                currentFragment.toggleCameraPreview()
            }
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val navController = (supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment)?.navController
                    if (navController?.popBackStack(R.id.homeFragment, false) == true) {
                        // Success
                    } else {
                        navController?.navigate(R.id.homeFragment)
                    }
                } catch (e: Exception) {
                    Log.e("Navigation", "Navigation error: ${e.message}")
                    super.onBackPressed()
                }
            }, 250)
            return
        }
        val navController = (supportFragmentManager.findFragmentById(R.id.fragment_container) as? NavHostFragment)?.navController
        if (navController?.navigateUp() != true) {
            super.onBackPressed()
        }
    }


}



