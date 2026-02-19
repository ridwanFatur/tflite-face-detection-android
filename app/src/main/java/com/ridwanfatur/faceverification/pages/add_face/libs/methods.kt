package com.ridwanfatur.faceverification.pages.add_face.libs

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.geometry.Size
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.ridwanfatur.faceverification.database.FaceItemDao
import com.ridwanfatur.faceverification.models.FaceItem
import com.ridwanfatur.faceverification.utils.DateUtils
import com.ridwanfatur.faceverification.utils.ImageUtils
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs


suspend fun onSaveFaceData(
    imageEmbedder: ImageEmbedder,
    faceItemDao: FaceItemDao,
    capturedBitmaps: List<Bitmap>,
    name: String
) {
    val embeddingArray = mutableListOf<ByteArray>()
    for (bitmapForEmbedding in capturedBitmaps) {
        val mpImageForEmbedding =
            BitmapImageBuilder(bitmapForEmbedding).build()
        val embeddingResult = imageEmbedder.embed(mpImageForEmbedding)
        val embedding =
            embeddingResult.embeddingResult().embeddings().firstOrNull()
        if (embedding != null) {
            val embeddingVector = embedding.quantizedEmbedding()
            if (embeddingVector != null) {
                embeddingArray.add(embeddingVector)
            }
        }
    }
    val created = DateUtils.getCreatedDate()
    val newFace = FaceItem(
        name = name,
        created = created,
        array = embeddingArray
    )
    faceItemDao.insert(newFace)
}

fun onTakePhoto(
    takePhoto: AtomicBoolean,
    bitmap: Bitmap,
    detections: List<Detection>,
    onAddCapturedBitmap: (Bitmap) -> Unit,
) {
    val bitmapForEmbedding = ImageUtils.getCroppedFaceImage(
        detections,
        bitmap = bitmap
    )
    if (bitmapForEmbedding != null) {
        onAddCapturedBitmap(bitmapForEmbedding)
        takePhoto.set(false)
    }
}

data class Point(
    var x: Float,
    var y: Float
)

enum class FaceDirection {
    UP, DOWN, RIGHT, LEFT, FORWARD, OUTSIDE_OF_CIRCLE
}

data class FaceAction(
    var name: FaceDirection,
    var count: Int
)

fun checkDetections(
    detections: List<Detection>,
    cameraSize: Size,
    imageSize: Size,
    onAction: (FaceDirection) -> Unit
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

    /** Keypoints */
    var leftEye = Point(-1f, -1f)
    var rightEye = Point(-1f, -1f)
    var nose = Point(-1f, -1f)
    var mouth = Point(-1f, -1f)
    var leftEar = Point(-1f, -1f)
    var rightEar = Point(-1f, -1f)

    val points = listOf(
        { p: Point -> leftEye = p },
        { p: Point -> rightEye = p },
        { p: Point -> nose = p },
        { p: Point -> mouth = p },
        { p: Point -> leftEar = p },
        { p: Point -> rightEar = p }
    )

    detection.keypoints().get().forEachIndexed { index, keypoint ->
        if (index < points.size) {
            val x = (keypoint.x() * imageSize.width * r) - paddingX
            val y = (keypoint.y() * imageSize.height * r) - paddingY
            points[index](Point(x, y))
        }
    }
    val keypoints = listOf(leftEye, rightEye, nose, mouth, leftEar, rightEar)

    /** Circle position */
    val circleRadius = minOf(cameraSize.width, cameraSize.height) / 2.5f
    val circleCenterX = cameraSize.width / 2
    val circleCenterY = cameraSize.height / 2
    fun checkPointInCircle(x: Float, y: Float) : Boolean {
        val dx = x - circleCenterX
        val dy = y - circleCenterY
        val distanceSquared = dx * dx + dy * dy
        return distanceSquared <= circleRadius * circleRadius
    }

    val allInCircle = keypoints.all { point ->
        checkPointInCircle(point.x, point.y)
    }

    if (!allInCircle) {
        onAction(FaceDirection.OUTSIDE_OF_CIRCLE)
    } else {
        val faceWidth = kotlin.math.abs(rightEye.x - leftEye.x)
        val faceHeight = kotlin.math.abs(mouth.y - ((leftEye.y + rightEye.y) / 2))

        val horizontalThresholdRatio = 0.3f
        val upThresholdRatio = 0.5f
        val downThresholdRatio = 0.1f

        val horizontalThreshold = faceWidth * horizontalThresholdRatio
        val upThreshold = faceHeight * upThresholdRatio
        val downThreshold = faceHeight * downThresholdRatio

        val faceCenterX = (leftEye.x + rightEye.x + nose.x) / 3
        val faceCenterY = (leftEye.y + rightEye.y + nose.y) / 3

        val horizontalOffset = faceCenterX - circleCenterX
        val verticalOffset = faceCenterY - circleCenterY

        val absHorizontalOffset = kotlin.math.abs(horizontalOffset)
        val absVerticalOffset = kotlin.math.abs(verticalOffset)

        val direction = if (absHorizontalOffset > absVerticalOffset) {
            if (absHorizontalOffset > horizontalThreshold) {
                if (horizontalOffset > 0) FaceDirection.RIGHT else FaceDirection.LEFT
            } else {
                FaceDirection.FORWARD
            }
        } else {
            if (verticalOffset < 0 && absVerticalOffset > upThreshold) {
                FaceDirection.UP
            } else if (verticalOffset > 0 && absVerticalOffset > downThreshold) {
                FaceDirection.DOWN
            } else {
                FaceDirection.FORWARD
            }
        }

        onAction(direction)
    }

}

