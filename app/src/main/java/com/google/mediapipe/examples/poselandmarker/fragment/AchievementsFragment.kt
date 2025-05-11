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
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.achievements.AchievementAdapter
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
        achievementManager.getAllAchievements(userId) { achievements ->
            achievementAdapter.updateAchievements(achievements)
        }
    }
}
