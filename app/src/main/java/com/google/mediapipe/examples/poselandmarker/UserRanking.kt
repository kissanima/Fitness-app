package com.google.mediapipe.examples.poselandmarker

data class UserRanking(
    val userName: String = "",
    val pushUpPoints: Int = 0,
    val crunchPoints: Int = 0,
    val plankPoints: Int = 0,
    val totalPoints: Int = 0,
    val achievementCount: Int = 0  // Add this field
)