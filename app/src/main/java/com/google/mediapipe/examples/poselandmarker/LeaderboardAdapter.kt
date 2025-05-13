package com.google.mediapipe.examples.poselandmarker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.UserRanking

class LeaderboardAdapter(private val rankings: MutableList<UserRanking>) :
    RecyclerView.Adapter<LeaderboardAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rankTextView: TextView = view.findViewById(R.id.textRank)
        val nameTextView: TextView = view.findViewById(R.id.textPlayerName)
        val statsTextView: TextView = view.findViewById(R.id.textExerciseStats)
        val pointsTextView: TextView = view.findViewById(R.id.textPoints)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_leaderboard_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ranking = rankings[position]

        holder.rankTextView.text = (position + 1).toString()
        holder.nameTextView.text = ranking.userName

        val statsText = "Push-ups: ${ranking.pushUpPoints}pts | " +
                "Crunches: ${ranking.crunchPoints}pts | " +
                "Plank: ${ranking.plankPoints}pts | " +
                "Achievements: ${ranking.achievementCount}"

        holder.statsTextView.text = statsText
        holder.pointsTextView.text = "${ranking.totalPoints} pts"
    }

    override fun getItemCount(): Int = rankings.size

    fun updateRankings(newRankings: List<UserRanking>) {
        rankings.clear()
        rankings.addAll(newRankings)
        notifyDataSetChanged()
    }
}
