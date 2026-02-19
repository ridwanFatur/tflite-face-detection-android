package com.ridwanfatur.faceverification.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.google.mediapipe.tasks.components.containers.Detection
import com.ridwanfatur.faceverification.utils.ImageUtils

@Composable
fun FaceDetectionCanvas(
    faceDetections: List<Detection>,
    cameraSize: Size,
    imageSize: Size,
) {
    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        drawFaceDetections(
            faceDetections,
            cameraSize,
            imageSize,
        )
    }
}


private fun DrawScope.drawFaceDetections(
    detections: List<Detection>,
    cameraSize: Size,
    imageSize: Size,
) {
    val detection = detections.firstOrNull() ?: return

    /** To get ratio and padding */
    val (r, paddingX, paddingY) = ImageUtils.calculateScaleAndPadding(cameraSize, imageSize)

    /** Bounding Box */
    val boundingBox = detection.boundingBox()

    val x1 = (boundingBox.left) * r - paddingX
    val y1 = (boundingBox.top) * r - paddingY
    val x2 = (boundingBox.right) * r - paddingX
    val y2 = (boundingBox.bottom) * r - paddingY

    drawRect(
        color = Color.Green,
        topLeft = androidx.compose.ui.geometry.Offset(x1, y1),
        size = Size(x2 - x1, y2 - y1),
        style = Stroke(width = 3.dp.toPx())
    )

    /** Keypoint */
    val colors = listOf(
        Color.Red,
        Color.Green,
        Color.Blue,
        Color.Yellow,
        Color.Magenta,
        Color.Cyan
    )

    detection.keypoints().get().forEachIndexed { index, keypoint ->
        val x = (keypoint.x() * imageSize.width * r) - paddingX
        val y = (keypoint.y() * imageSize.height * r) - paddingY
        val color = colors[index % colors.size]

        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = androidx.compose.ui.geometry.Offset(x, y)
        )
    }
}