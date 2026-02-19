package com.ridwanfatur.faceverification.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Size
import com.google.mediapipe.tasks.components.containers.Detection
import kotlin.math.max

class ImageUtils {
    companion object {
        fun getCroppedFaceImage(
            detections: List<Detection>,
            bitmap: Bitmap
        ): Bitmap? {
            val detection = detections.firstOrNull() ?: return null

            /** Bounding Box */
            val boundingBox = detection.boundingBox()

            val left = boundingBox.left.coerceAtLeast(0f).toInt()
            val top = boundingBox.top.coerceAtLeast(0f).toInt()
            val right = boundingBox.right.coerceAtMost(bitmap.width.toFloat()).toInt()
            val bottom = boundingBox.bottom.coerceAtMost(bitmap.height.toFloat()).toInt()

            val width = (right - left).coerceAtLeast(1)
            val height = (bottom - top).coerceAtLeast(1)

            return Bitmap.createBitmap(bitmap, left, top, width, height)
        }

        fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
            val bitmap = imageProxy.toBitmap()
            val degrees = imageProxy.imageInfo.rotationDegrees
            val matrix = Matrix()
            matrix.postRotate(degrees.toFloat())
            matrix.postScale(-1f, 1f)
            return Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        }

        fun calculateScaleAndPadding(cameraSize: Size, imageSize: Size): Triple<Float, Float, Float> {
            val rx = cameraSize.width / imageSize.width
            val ry = cameraSize.height / imageSize.height
            val r = max(rx, ry)

            val resizedImageWidth = imageSize.width * r
            val resizedImageHeight = imageSize.height * r

            var paddingX = 0f
            var paddingY = 0f

            if (resizedImageHeight > resizedImageWidth) {
                paddingX = (resizedImageWidth - cameraSize.width) / 2.0f
            } else {
                paddingY = (resizedImageHeight - cameraSize.height) / 2.0f
            }

            return Triple(r, paddingX, paddingY)
        }
    }
}