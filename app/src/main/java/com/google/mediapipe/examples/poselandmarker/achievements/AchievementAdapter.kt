package com.google.mediapipe.examples.poselandmarker.achievements

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.R
import android.util.Log
import com.google.mediapipe.examples.poselandmarker.achievements.Achievement

class AchievementAdapter(private val achievements: MutableList<Achievement>) :
    RecyclerView.Adapter<AchievementAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.textAchievementTitle)
        val descriptionTextView: TextView = view.findViewById(R.id.textAchievementDescription)
        val iconImageView: ImageView = view.findViewById(R.id.imageAchievement)
        val statusTextView: TextView = view.findViewById(R.id.textAchievementStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val achievement = achievements[position]
        holder.titleTextView.text = achievement.title
        holder.descriptionTextView.text = achievement.description

        // Set icon based on achievement type or use default
        holder.iconImageView.setImageResource(achievement.iconResource)

        // Set status text
        if (achievement.isUnlocked) {
            holder.statusTextView.text = "UNLOCKED"
            holder.statusTextView.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_light))
        } else {
            holder.statusTextView.text = "LOCKED"
            holder.statusTextView.setTextColor(holder.itemView.context.getColor(android.R.color.darker_gray))
        }

        // Debugging - add this
        Log.d("AchievementAdapter", "Binding achievement: ${achievement.id}, isUnlocked: ${achievement.isUnlocked}")
    }

    override fun getItemCount() = achievements.size

    fun updateAchievements(newAchievements: List<Achievement>) {
        achievements.clear()
        achievements.addAll(newAchievements)
        notifyDataSetChanged()
    }
}