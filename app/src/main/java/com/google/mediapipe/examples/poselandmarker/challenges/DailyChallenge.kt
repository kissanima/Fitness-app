package com.google.mediapipe.examples.poselandmarker.challenges

import com.google.firebase.database.IgnoreExtraProperties
import java.util.*

@IgnoreExtraProperties
data class DailyChallenge(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val exerciseType: String = "",
    val targetCount: Int = 0,
    val pointsReward: Int = 0,
    val completed: Boolean = false,
    val dateCreated: Long = System.currentTimeMillis(),
    val progress: Int = 0
)
