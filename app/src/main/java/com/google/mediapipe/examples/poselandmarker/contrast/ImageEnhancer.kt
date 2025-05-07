package com.google.mediapipe.examples.poselandmarker.contrast

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

class ImageEnhancer {
    companion object {
        init {
            System.loadLibrary("opencv_java4")
        }

        fun enhanceContrast(bitmap: Bitmap, alpha: Double = 1.5, beta: Int = 0): Bitmap {
            val src = Mat()
            val dst = Mat()
            val resultBitmap = bitmap.copy(bitmap.config, true)

            Utils.bitmapToMat(bitmap, src)
            // Apply contrast enhancement (alpha) and brightness (beta)
            src.convertTo(dst, -1, alpha, beta.toDouble())
            Utils.matToBitmap(dst, resultBitmap)

            src.release()
            dst.release()

            return resultBitmap
        }

        fun applyCLAHE(bitmap: Bitmap, clipLimit: Double = 2.0): Bitmap {
            val src = Mat()
            val lab = Mat()
            val resultBitmap = Bitmap.createBitmap(
                bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888
            )

            Utils.bitmapToMat(bitmap, src)

            // Convert RGBA to RGB
            val rgbMat = Mat()
            Imgproc.cvtColor(src, rgbMat, Imgproc.COLOR_RGBA2RGB)

            // Convert RGB to LAB
            Imgproc.cvtColor(rgbMat, lab, Imgproc.COLOR_RGB2Lab)

            // Split LAB channels
            val labChannels = ArrayList<Mat>()
            Core.split(lab, labChannels)

            // Apply CLAHE to L-channel
            val clahe = Imgproc.createCLAHE(clipLimit, Size(8.0, 8.0))
            clahe.apply(labChannels[0], labChannels[0])

            // Merge channels back
            Core.merge(labChannels, lab)

            // Convert back to RGB
            Imgproc.cvtColor(lab, rgbMat, Imgproc.COLOR_Lab2RGB)

            // Convert RGB to RGBA (if needed)
            Imgproc.cvtColor(rgbMat, src, Imgproc.COLOR_RGB2RGBA)

            Utils.matToBitmap(src, resultBitmap)

            // Release resources
            src.release()
            lab.release()
            rgbMat.release()
            labChannels.forEach { it.release() }

            return resultBitmap
        }

    }
}