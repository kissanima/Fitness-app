package com.google.mediapipe.examples.poselandmarker

data class UserRanking(
    val userName: String = "",
    val totalPoints: Int = 0,
    val pushUpPoints : Int = 0,  // Note: capital 'U' to match Firebase structure
    val crunchPoints: Int = 0,
    val plankPoints: Int = 0
)