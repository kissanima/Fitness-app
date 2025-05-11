package com.google.mediapipe.examples.poselandmarker.achievements

import com.google.firebase.database.IgnoreExtraProperties
import com.google.mediapipe.examples.poselandmarker.R

@IgnoreExtraProperties
data class Achievement(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var exerciseType: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var isUnlocked: Boolean = false,
    var iconResource: Int = R.drawable.trophy,
    var value: Int = 0,
    var count: Int = 0,
    var exercise: String = ""
) {
    constructor() : this("", "", "", "", System.currentTimeMillis(), false, 0, 0, 0, "")
}
