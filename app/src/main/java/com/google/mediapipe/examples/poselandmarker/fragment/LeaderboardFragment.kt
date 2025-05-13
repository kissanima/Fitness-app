package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.mediapipe.examples.poselandmarker.LeaderboardAdapter
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.UserRanking

class LeaderboardFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var database: FirebaseDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_leaderboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.recyclerViewLeaderboard)
        database = FirebaseDatabase.getInstance()

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = LeaderboardAdapter(mutableListOf()) // Initialize with empty mutable list

        loadLeaderboardData()
    }

    private fun loadLeaderboardData() {
        val leaderboardRef = database.getReference("leaderboard")
        leaderboardRef.orderByChild("totalPoints").limitToLast(20)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val userRankings = mutableListOf<UserRanking>()

                        // Manual parsing to avoid deserialization issues
                        for (userSnapshot in snapshot.children) {
                            try {
                                val userName = userSnapshot.child("userName").getValue(String::class.java) ?: "Anonymous"
                                val pushUpPoints = userSnapshot.child("pushUpPoints").getValue(Int::class.java) ?: 0
                                val crunchPoints = userSnapshot.child("crunchPoints").getValue(Int::class.java) ?: 0
                                val plankPoints = userSnapshot.child("plankPoints").getValue(Int::class.java) ?: 0
                                val totalPoints = userSnapshot.child("totalPoints").getValue(Int::class.java) ?: 0
                                val achievementCount = userSnapshot.child("achievementCount").getValue(Int::class.java) ?: 0

                                val userRanking = UserRanking(
                                    userName = userName,
                                    pushUpPoints = pushUpPoints,
                                    crunchPoints = crunchPoints,
                                    plankPoints = plankPoints,
                                    totalPoints = totalPoints,
                                    achievementCount = achievementCount
                                )

                                userRankings.add(userRanking)
                            } catch (e: Exception) {
                                Log.e("LeaderboardFragment", "Error parsing user: ${userSnapshot.key}", e)
                            }
                        }

                        // Sort rankings by total points (descending)
                        userRankings.sortByDescending { it.totalPoints }

                        // Update adapter with the sorted list
                        (recyclerView.adapter as LeaderboardAdapter).updateRankings(userRankings)
                    } catch (e: Exception) {
                        Log.e("LeaderboardFragment", "Error processing leaderboard data", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("LeaderboardFragment", "Error loading leaderboard data", error.toException())
                }
            })
    }
}

