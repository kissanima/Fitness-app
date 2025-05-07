package com.google.mediapipe.examples.poselandmarker.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
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

        loadLeaderboardData()
    }

    private fun loadLeaderboardData() {
        val leaderboardRef = database.getReference("leaderboard")
        leaderboardRef.orderByChild("totalPoints").limitToLast(20)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userRankings = mutableListOf<UserRanking>()

                    for (userSnapshot in snapshot.children) {
                        val user = userSnapshot.getValue(UserRanking::class.java)
                        user?.let { userRankings.add(it) }
                    }

                    userRankings.sortByDescending { it.totalPoints }
                    recyclerView.adapter = LeaderboardAdapter(userRankings)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("LeaderboardFragment", "Error loading leaderboard data", error.toException())
                }
            })
    }
}
