package com.google.mediapipe.examples.poselandmarker.achievements

object AchievementConstants {
    // Push-up achievements
    val PUSHUP_ACHIEVEMENTS = mapOf(
        "pushup_beginner" to AchievementInfo(
            title = "Push-up Beginner",
            description = "Complete 10 push-ups",
            threshold = 10
        ),
        "pushup_intermediate" to AchievementInfo(
            title = "Push-up Intermediate",
            description = "Complete 50 push-ups",
            threshold = 50
        ),
        "pushup_master" to AchievementInfo(
            title = "Push-up Master",
            description = "Complete 100 push-ups",
            threshold = 100
        ),
        "pushup_streak_10" to AchievementInfo(
            title = "Push-up Streak",
            description = "10 push-ups in a row",
            threshold = 10
        )
    )

    // Plank achievements
    val PLANK_ACHIEVEMENTS = mapOf(
        "plank_beginner" to AchievementInfo(
            title = "Plank Beginner",
            description = "Hold a plank for 30 seconds",
            threshold = 30
        ),
        "plank_intermediate" to AchievementInfo(
            title = "Plank Intermediate",
            description = "Hold a plank for 60 seconds",
            threshold = 60
        ),
        "plank_master" to AchievementInfo(
            title = "Plank Master",
            description = "Hold a plank for 120 seconds",
            threshold = 120
        )
    )

    // Challenge achievements
    val CHALLENGE_ACHIEVEMENTS = mapOf(
        "challenge_streak_3" to AchievementInfo(
            title = "Challenge Streak",
            description = "Complete 3 daily challenges in a row",
            threshold = 3
        ),
        "challenge_master" to AchievementInfo(
            title = "Challenge Master",
            description = "Complete 10 daily challenges",
            threshold = 10
        )
    )

    data class AchievementInfo(
        val title: String,
        val description: String,
        val threshold: Int
    )
}
