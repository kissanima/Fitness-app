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
import com.google.mediapipe.examples.poselandmarker.challenges.ChallengeAdapter
import com.google.mediapipe.examples.poselandmarker.challenges.DailyChallengeManager

class DailyChallengesFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var challengeAdapter: ChallengeAdapter
    private lateinit var challengeManager: DailyChallengeManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_daily_challenges, container, false)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerViewChallenges)
        challengeManager = DailyChallengeManager(FirebaseDatabase.getInstance())

        // Setup RecyclerView and load challenges
        setupRecyclerView()
        loadChallenges()
    }

    private fun setupRecyclerView() {
        challengeAdapter = ChallengeAdapter(mutableListOf())
        recyclerView.adapter = challengeAdapter
        recyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun loadChallenges() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        // Query all challenges - both active and completed
        challengeManager.getUserChallenges(userId) { challenges ->
            challengeAdapter.updateChallenges(challenges)
        }
    }
}
