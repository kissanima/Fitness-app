package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.achievements.AchievementAdapter
import com.google.mediapipe.examples.poselandmarker.achievements.AchievementConstants
import com.google.mediapipe.examples.poselandmarker.achievements.AchievementManager

class AchievementsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var achievementAdapter: AchievementAdapter
    private lateinit var achievementManager: AchievementManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_achievements, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewAchievements)
        achievementManager = AchievementManager(FirebaseDatabase.getInstance())

        setupRecyclerView()
        loadAchievements()
    }

    private fun setupRecyclerView() {
        achievementAdapter = AchievementAdapter(mutableListOf())
        recyclerView.adapter = achievementAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun loadAchievements() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Initialize achievements before loading them
        initializeAchievements(userId)

        // Then load all achievements
        achievementManager.getAllAchievements(userId) { achievements ->
            achievementAdapter.updateAchievements(achievements)
        }
    }

    private fun initializeAchievements(userId: String) {
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(userId)

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



}
