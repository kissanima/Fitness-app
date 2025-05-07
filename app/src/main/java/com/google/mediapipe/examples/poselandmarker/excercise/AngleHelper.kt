
package com.google.mediapipe.examples.poselandmarker.excercise

import java.lang.Math.toDegrees
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.abs

class AngleHelper {

    data class PointF(val x: Float, val y: Float)

    fun calculateAngle(a: PointF, b: PointF, c: PointF): Float {
        // Vectors: BA = A - B and BC = C - B
        val vectorBAx = a.x - b.x
        val vectorBAy = a.y - b.y
        val vectorBCx = c.x - b.x
        val vectorBCy = c.y - b.y

        val dotProduct = vectorBAx * vectorBCx + vectorBAy * vectorBCy
        val magnitudeBA = sqrt(vectorBAx * vectorBAx + vectorBAy * vectorBAy)
        val magnitudeBC = sqrt(vectorBCx * vectorBCx + vectorBCy * vectorBCy)

        // Avoid division by zero
        if (magnitudeBA == 0f || magnitudeBC == 0f) return 0f

        val angleRad = acos(dotProduct / (magnitudeBA * magnitudeBC))
        return toDegrees(angleRad.toDouble()).toFloat()
    }

    fun isPlank(
        leftShoulder: PointF, rightShoulder: PointF,
        leftHip: PointF, rightHip: PointF,
        leftAnkle: PointF, rightAnkle: PointF,
        leftElbow: PointF, rightElbow: PointF,
        leftWrist: PointF, rightWrist: PointF
    ): Boolean {
        // Calculate angles we'll need for detection
        val leftHipAngle = calculateAngle(leftShoulder, leftHip, leftAnkle)
        val rightHipAngle = calculateAngle(rightShoulder, rightHip, rightAnkle)
        val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
        val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)

        // 1. First, check if we have a straight body (this applies to both plank types)
        val hipAnglesValid = leftHipAngle > 150f && rightHipAngle > 150f
        if (!hipAnglesValid) return false

        // 2. Check for standard plank (straight arms)
        val isStandardPlank = leftElbowAngle > 155f && rightElbowAngle > 155f

        if (isStandardPlank) {
            // For standard plank, check body alignment
            val shoulderAvgY = (leftShoulder.y + rightShoulder.y) / 2f
            val hipAvgY = (leftHip.y + rightHip.y) / 2f
            val ankleAvgY = (leftAnkle.y + rightAnkle.y) / 2f

            val bodyAlignmentValid = abs(shoulderAvgY - hipAvgY) <= 0.12f &&
                    abs(hipAvgY - ankleAvgY) <= 0.12f

            return bodyAlignmentValid
        }

        // 3. Check for elbow plank (bent arms) - IMPROVED VERSION
        val isElbowPlank = (leftElbowAngle >= 65f && leftElbowAngle <= 120f &&
                rightElbowAngle >= 65f && rightElbowAngle <= 120f)

        if (isElbowPlank) {
            // For elbow plank, check modified body alignment
            val shoulderAvgY = (leftShoulder.y + rightShoulder.y) / 2f
            val hipAvgY = (leftHip.y + rightHip.y) / 2f
            val ankleAvgY = (leftAnkle.y + rightAnkle.y) / 2f

            // Allow shoulders to be lower than hips (typical in elbow plank)
            val modifiedAlignmentValid =
                (shoulderAvgY - hipAvgY) <= 0.2f && (shoulderAvgY - hipAvgY) >= -0.2f &&
                        abs(hipAvgY - ankleAvgY) <= 0.15f

            // More forgiving forearm position check
            val leftForearmHorizontal = abs(leftElbow.y - leftWrist.y) <= 0.15f
            val rightForearmHorizontal = abs(rightElbow.y - rightWrist.y) <= 0.15f

            // More forgiving position check - note the OR operator
            val forearmsUnderShoulders =
                abs(leftShoulder.x - leftElbow.x) <= 0.25f ||
                        abs(rightShoulder.x - rightElbow.x) <= 0.25f

            // Only need one forearm to be roughly horizontal
            return modifiedAlignmentValid &&
                    (leftForearmHorizontal || rightForearmHorizontal) &&
                    forearmsUnderShoulders
        }

        return false
    }









    // Add this to your AngleHelper class
    data class PushupState(val isTopPosition: Boolean, val isBottomPosition: Boolean)



}