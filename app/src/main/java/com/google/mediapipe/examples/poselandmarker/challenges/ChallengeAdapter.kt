package com.google.mediapipe.examples.poselandmarker.challenges

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.R

class ChallengeAdapter(private val challenges: MutableList<DailyChallenge>) :
    RecyclerView.Adapter<ChallengeAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.textChallengeTitle)
        val descriptionTextView: TextView = view.findViewById(R.id.textChallengeDescription)
        val progressBar: ProgressBar = view.findViewById(R.id.challengeProgress)
        val rewardTextView: TextView = view.findViewById(R.id.textChallengeReward)
        val statusTextView: TextView = view.findViewById(R.id.textChallengeStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_challenge, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val challenge = challenges[position]
        holder.titleTextView.text = challenge.title
        holder.descriptionTextView.text = challenge.description
        holder.rewardTextView.text = "${challenge.pointsReward} points"

        // Use the progress value from the database
        if (!challenge.completed) {
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = challenge.progress
            holder.statusTextView.text = "${challenge.progress}%"
        } else {
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = 100
            holder.statusTextView.text = "Completed"
            holder.statusTextView.setTextColor(Color.GREEN)
        }
    }


    override fun getItemCount() = challenges.size

    fun updateChallenges(newChallenges: List<DailyChallenge>) {
        challenges.clear()
        challenges.addAll(newChallenges)
        notifyDataSetChanged()
    }
}
