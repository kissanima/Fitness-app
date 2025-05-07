
package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    private var textPaint = Paint()

    init {
        initPaints()
    }

    fun clear() {
        results = null
        pointPaint.reset()
        linePaint.reset()
        invalidate()
        initPaints()
    }


    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL

        // Set up text paint for coordinates
        textPaint.color = Color.WHITE  // Use a contrasting color
        textPaint.textSize = 30f         // Adjust text size as needed
        textPaint.style = Paint.Style.FILL
    }

    // Define a set of landmark indices to skip
    val skipIndices = setOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 17, 18, 19, 20, 21, 22, 29, 30, 31, 32)

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { poseLandmarkerResult ->
            for(landmark in poseLandmarkerResult.landmarks()) {
                landmark.forEachIndexed { index, normalizedLandmark ->
                    // Calculate scaled coordinates
                    val x = normalizedLandmark.x() * imageWidth * scaleFactor
                    val y = normalizedLandmark.y() * imageHeight * scaleFactor



                    // Only draw text for landmarks with index greater than 10
                    if (!skipIndices.contains(index)) {

                        // Draw the landmark point
                        canvas.drawPoint(x, y, pointPaint)

                        val coordText = "(${String.format("%.2f", normalizedLandmark.x())}, " +
                                "${String.format("%.2f", normalizedLandmark.y())})"
                        canvas.drawText(coordText, x + 10, y - 10, textPaint)
                    }
                }


                // Draw the connections (lines) between landmarks, skipping those involving landmarks 0–10.
                PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
                    connection?.let {
                        // Skip if either endpoint is in the skipIndices set
                        if (skipIndices.contains(it.start()) || skipIndices.contains(it.end())) {
                            return@forEach
                        }

                        canvas.drawLine(
                            poseLandmarkerResult.landmarks()[0][connection.start()].x() * imageWidth * scaleFactor,
                            poseLandmarkerResult.landmarks()[0][connection.start()].y() * imageHeight * scaleFactor,
                            poseLandmarkerResult.landmarks()[0][connection.end()].x() * imageWidth * scaleFactor,
                            poseLandmarkerResult.landmarks()[0][connection.end()].y() * imageHeight * scaleFactor,
                            linePaint
                        )
                    }
                }

            }
        }
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = poseLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12F
    }


}