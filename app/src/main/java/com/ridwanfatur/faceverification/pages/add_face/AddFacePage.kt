package com.ridwanfatur.faceverification.pages.add_face

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Detection
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.ridwanfatur.faceverification.ModelPathConstants
import com.ridwanfatur.faceverification.components.FaceDetectionCanvas
import com.ridwanfatur.faceverification.components.FrontCameraPreview
import com.ridwanfatur.faceverification.database.AppDatabase
import com.ridwanfatur.faceverification.pages.add_face.components.FaceImagePreview
import com.ridwanfatur.faceverification.pages.add_face.libs.onSaveFaceData
import com.ridwanfatur.faceverification.pages.add_face.libs.onTakePhoto
import com.ridwanfatur.faceverification.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import com.ridwanfatur.faceverification.pages.add_face.components.CircularCameraOverlay
import com.ridwanfatur.faceverification.pages.add_face.libs.FaceAction
import com.ridwanfatur.faceverification.pages.add_face.libs.FaceDirection
import com.ridwanfatur.faceverification.pages.add_face.libs.checkDetections

enum class AddFaceState {
    CAMERA,
    LOADING,
    PREVIEW
}

@Composable
fun AddFacePage(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var cameraSize by remember { mutableStateOf<Size?>(null) }
    var imageSize by remember { mutableStateOf<Size?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val isProcessing = remember { AtomicBoolean(false) }
    val addFaceState = remember { mutableStateOf(AddFaceState.CAMERA) }
    val capturedBitmaps = remember { mutableStateListOf<Bitmap>() }

    var faceDetections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val db = AppDatabase.getInstance(context)
    val faceItemDao = db.faceItemDao()

    val takePhoto = remember { AtomicBoolean(false) }

    val faceDetector = remember {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(ModelPathConstants.FACE_DETECTION_SHORT_RANGE)

        val optionsBuilder = FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setMinDetectionConfidence(0.7f)
            .setMinSuppressionThreshold(0.5f)

        FaceDetector.createFromOptions(context, optionsBuilder.build())
    }

    val imageEmbedder = remember {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(ModelPathConstants.IMAGE_EMBEDDING)

        val optionsBuilder = ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setQuantize(true)

        ImageEmbedder.createFromOptions(context, optionsBuilder.build())
    }

    /** Face Action */
    val faceActionList = listOf(
        FaceAction(FaceDirection.FORWARD, 20),
        FaceAction(FaceDirection.LEFT, 20),
        FaceAction(FaceDirection.RIGHT, 20),
        FaceAction(FaceDirection.UP, 20),
        FaceAction(FaceDirection.DOWN, 20)
    )
    val indexAction = remember { mutableStateOf(0) }
    val countDirection = remember { mutableStateOf(0) }
    val currentDirection = remember { mutableStateOf(FaceDirection.OUTSIDE_OF_CIRCLE) }

    fun onHandleFaceDirection(direction: FaceDirection) {
        if (addFaceState.value == AddFaceState.CAMERA) {
            if (direction == FaceDirection.OUTSIDE_OF_CIRCLE) {
                indexAction.value = 0
                countDirection.value = 0
                capturedBitmaps.clear()
            } else {
                if (indexAction.value != -1) {
                    val currentAction = faceActionList[indexAction.value]
                    if (currentAction.name == direction) {
                        if (countDirection.value >= currentAction.count) {
                            if (indexAction.value < faceActionList.size - 1) {
                                scope.launch { takePhoto.set(true) }
                                indexAction.value += 1
                                countDirection.value = 0
                            } else {
                                scope.launch { takePhoto.set(true) }
                                indexAction.value = -1
                                countDirection.value = 0
                            }
                        } else {
                            countDirection.value += 1
                        }
                    } else {
                        countDirection.value = 0
                    }
                } else {
                    addFaceState.value = AddFaceState.PREVIEW
                }
            }
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onSizeChanged { size ->
                    if (cameraSize == null) {
                        cameraSize = Size(size.width.toFloat(), size.height.toFloat())
                    }
                }
        ) {
            when (addFaceState.value) {
                AddFaceState.CAMERA -> {
                    if (cameraSize != null) {
                        FrontCameraPreview(
                            onImageProxy = { imageProxy ->
                                if (!isProcessing.getAndSet(true)) {
                                    coroutineScope.launch {
                                        withContext(Dispatchers.Default) {
                                            val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)
                                            if (imageSize == null) {
                                                imageSize = Size(
                                                    bitmap.width.toFloat(),
                                                    bitmap.height.toFloat()
                                                )
                                            }

                                            val mpImage = BitmapImageBuilder(bitmap).build()
                                            val result = faceDetector.detect(mpImage)
                                            val detections = result.detections()
                                            faceDetections = detections
                                            checkDetections(
                                                detections,
                                                cameraSize!!,
                                                imageSize!!,
                                                onAction = { direction ->
                                                    currentDirection.value = direction
                                                    onHandleFaceDirection(direction)
                                                }
                                            )

                                            if (takePhoto.get()) {
                                                onTakePhoto(
                                                    takePhoto,
                                                    bitmap,
                                                    detections = result.detections(),
                                                    onAddCapturedBitmap = { bitmapForEmbedding ->
                                                        capturedBitmaps.add(bitmapForEmbedding)
                                                    }
                                                )
                                            }

                                            imageProxy.close()
                                            isProcessing.set(false)
                                        }
                                    }
                                } else {
                                    imageProxy.close()
                                }
                            },
                            lifecycleOwner = lifecycleOwner,
                            tag = "AddFacePage"
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val radius = minOf(canvasWidth, canvasHeight) / 2.5f

                            val centerX = canvasWidth / 2
                            val centerY = canvasHeight / 2

                            val path = Path().apply {
                                addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
                                addOval(
                                    Rect(
                                        centerX - radius,
                                        centerY - radius,
                                        centerX + radius,
                                        centerY + radius
                                    )
                                )
                                fillType = PathFillType.EvenOdd
                            }
                            drawPath(path, color = Color.White)

                            if (indexAction.value != -1) {
                                val currentAction = faceActionList[indexAction.value]
                                val sweepAngle =
                                    (countDirection.value.toFloat() / currentAction.count.toFloat()) * 360f
                                drawArc(
                                    color = Color.Green,
                                    startAngle = -90f,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 20f),
                                    topLeft = androidx.compose.ui.geometry.Offset(
                                        centerX - radius,
                                        centerY - radius
                                    ),
                                    size = Size(radius * 2, radius * 2)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(
                                    modifier = Modifier.height(
                                        (minOf(cameraSize!!.width, cameraSize!!.height) / 2.5f).dp
                                    )
                                )
                                if (indexAction.value != -1) {
                                    val currentAction = faceActionList[indexAction.value]
                                    Text(
                                        text = when (currentAction.name) {
                                            FaceDirection.UP -> "Tolong angkat wajahmu ke atas"
                                            FaceDirection.DOWN -> "Tolong turunkan wajahmu ke bawah"
                                            FaceDirection.LEFT -> "Tolong hadapkan wajah ke kiri"
                                            FaceDirection.RIGHT -> "Tolong hadapkan wajah ke kanan"
                                            FaceDirection.FORWARD -> "Tolong hadapkan wajah lurus ke depan"
                                            FaceDirection.OUTSIDE_OF_CIRCLE -> "Arahkan wajahmu ke dalam lingkaran"
                                        },
                                        color = Color.Black,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                AddFaceState.LOADING -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Save face data")
                    }
                }

                AddFaceState.PREVIEW -> {
                    FaceImagePreview(
                        capturedBitmaps,
                        onBack = {
                            addFaceState.value = AddFaceState.CAMERA
                            capturedBitmaps.clear()
                            countDirection.value = 0
                            indexAction.value = 0
                        },
                        onAccept = { name ->
                            addFaceState.value = AddFaceState.LOADING
                            scope.launch {
                                onSaveFaceData(
                                    imageEmbedder,
                                    faceItemDao,
                                    capturedBitmaps,
                                    name
                                )
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
        }
    }
}

