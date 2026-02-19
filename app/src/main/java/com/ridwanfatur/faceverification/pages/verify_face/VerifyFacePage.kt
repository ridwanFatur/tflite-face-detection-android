package com.ridwanfatur.faceverification.pages.verify_face

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.ridwanfatur.faceverification.ModelPathConstants
import com.ridwanfatur.faceverification.components.FrontCameraPreview
import com.ridwanfatur.faceverification.models.FaceItem
import com.ridwanfatur.faceverification.models.FaceItemWithEmbedding
import com.ridwanfatur.faceverification.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Optional
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay

@Composable
fun VerifyFacePage(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var cameraSize by remember { mutableStateOf<Size?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val isProcessing = remember { AtomicBoolean(false) }
    var faceThreshold by remember { mutableStateOf(0.8f) }
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var detectionCount by remember { mutableStateOf(0) }
    var lastDetectionTime by remember { mutableStateOf(0L) }
    var isDetectionActive by remember { mutableStateOf(false) }

    val animatedThreshold by animateFloatAsState(
        targetValue = faceThreshold,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "threshold"
    )

    val settingsRotation by animateFloatAsState(
        targetValue = if (isSettingsExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "settings_rotation"
    )

    val detectionPulse by animateFloatAsState(
        targetValue = if (isDetectionActive) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "detection_pulse"
    )

    val gson = remember { Gson() }
    val savedStateHandle = navController.previousBackStackEntry?.savedStateHandle
    val faceItemsJson = savedStateHandle?.get<String>("faceItems")
    val faceItems: List<FaceItemWithEmbedding> = faceItemsJson?.let {
        val type = object : TypeToken<List<FaceItem>>() {}.type
        val parsedFaceItems: List<FaceItem> = gson.fromJson(it, type)
        parsedFaceItems.map { faceItem ->
            val embeddings = faceItem.array.map { vector ->
                Embedding.create(
                    FloatArray(0),
                    vector,
                    0,
                    Optional.empty()
                )
            }
            FaceItemWithEmbedding(faceItem, embeddings)
        }
    } ?: emptyList()

    val detectedFaceList = remember { mutableStateOf<List<DetectedFace>>(emptyList()) }

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

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black,
                            Color(0xFF1A1A2E)
                        )
                    )
                )
                .padding(paddingValues)
                .onSizeChanged { size ->
                    if (cameraSize == null) {
                        cameraSize = Size(size.width.toFloat(), size.height.toFloat())
                    }
                }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Box {
                    FrontCameraPreview(
                        onImageProxy = { imageProxy ->
                            if (!isProcessing.getAndSet(true)) {
                                coroutineScope.launch {
                                    withContext(Dispatchers.Default) {
                                        val currentTime = System.currentTimeMillis()
                                        isDetectionActive = true

                                        val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)
                                        val mpImage = BitmapImageBuilder(bitmap).build()
                                        val result = faceDetector.detect(mpImage)
                                        val detections = result.detections()

                                        val bitmapForEmbedding = ImageUtils.getCroppedFaceImage(
                                            detections,
                                            bitmap = bitmap
                                        )

                                        if (bitmapForEmbedding != null) {
                                            val mpImageForEmbedding =
                                                BitmapImageBuilder(bitmapForEmbedding).build()
                                            val embeddingResult = imageEmbedder.embed(mpImageForEmbedding)
                                            val embedding =
                                                embeddingResult.embeddingResult().embeddings().firstOrNull()

                                            if (embedding != null) {
                                                processImageEmbedding(
                                                    embedding,
                                                    onUpdateList = { newList ->
                                                        detectedFaceList.value = newList
                                                        detectionCount++
                                                        lastDetectionTime = currentTime
                                                    },
                                                    faceItems,
                                                    faceThreshold.toDouble(),
                                                )
                                            } else {
                                                detectedFaceList.value = emptyList()
                                            }
                                        } else {
                                            detectedFaceList.value = emptyList()
                                        }

                                        delay(100)
                                        isDetectionActive = false
                                        imageProxy.close()
                                        isProcessing.set(false)
                                    }
                                }
                            } else {
                                imageProxy.close()
                            }
                        },
                        lifecycleOwner = lifecycleOwner,
                        tag = "VerifyFacePage"
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .widthIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(detectedFaceList.value) { index, face ->
                    DetectionCard(
                        face = face,
                        animationDelay = index * 100,
                        threshold = faceThreshold
                    )
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSettingsExpanded = !isSettingsExpanded }
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Detection Settings",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.Cyan,
                            modifier = Modifier
                                .rotate(settingsRotation)
                                .size(24.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = isSettingsExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Text(
                                text = "Confidence Threshold",
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = "Low",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )

                                Slider(
                                    value = faceThreshold,
                                    onValueChange = {
                                        faceThreshold = it
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            (context as? ComponentActivity)?.let { activity ->
                                                activity.window.decorView.performHapticFeedback(
                                                    HapticFeedbackConstants.KEYBOARD_TAP
                                                )
                                            }
                                        }
                                    },
                                    valueRange = -1f..1f,
                                    steps = 19,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.Cyan,
                                        activeTrackColor = Color.Cyan,
                                        inactiveTrackColor = Color.Gray
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 16.dp)
                                )

                                Text(
                                    text = "High",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = getThresholdColor(animatedThreshold)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Current: ${String.format("%.2f", animatedThreshold)}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCard("Detections", detectionCount.toString(), Icons.Default.Face)
                        StatCard("Active", if (isDetectionActive) "YES" else "NO", Icons.Default.Info)
                        StatCard("Faces", detectedFaceList.value.size.toString(), Icons.Default.Person)
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    detectionCount = 0
                    detectedFaceList.value = emptyList()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(24.dp),
                containerColor = Color.Cyan
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
fun DetectionCard(
    face: DetectedFace,
    animationDelay: Int,
    threshold: Float
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(face) {
        delay(animationDelay.toLong())
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeIn()
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = getConfidenceColor(face.confidence)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = face.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "ID: ${face.id}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )

                LinearProgressIndicator(
                    progress = { (face.confidence + 1f) / 2f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                Text(
                    text = String.format("%.2f", face.confidence),
                    color = Color.White,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Gray.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Cyan,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

fun getThresholdColor(threshold: Float): Color {
    return when {
        threshold < -0.5f -> Color.Red.copy(alpha = 0.7f)
        threshold < 0f -> Color.Blue.copy(alpha = 0.7f)
        threshold < 0.5f -> Color.Yellow.copy(alpha = 0.7f)
        else -> Color.Green.copy(alpha = 0.7f)
    }
}

fun getConfidenceColor(confidence: Float): Color {
    return when {
        confidence < -0.5f -> Color.Red.copy(alpha = 0.8f)
        confidence < 0f -> Color.Blue.copy(alpha = 0.8f)
        confidence < 0.5f -> Color.Yellow.copy(alpha = 0.8f)
        else -> Color.Green.copy(alpha = 0.8f)
    }
}

data class DetectedFace(
    val name: String,
    val id: String,
    val confidence: Float
)

fun processImageEmbedding(
    embedding: Embedding,
    onUpdateList: (List<DetectedFace>) -> Unit,
    faceItems: List<FaceItemWithEmbedding>,
    threshold: Double
) {
    val embeddingVector = embedding.quantizedEmbedding()

    if (embeddingVector != null) {
        val resultList = faceItems.mapNotNull { faceItem ->
            val maxSimilarity = faceItem.embeddings
                .map { faceEmbedding ->
                    ImageEmbedder.cosineSimilarity(faceEmbedding, embedding)
                }
                .maxOrNull() ?: 0.0

            if (maxSimilarity > threshold) {
                DetectedFace(
                    name = faceItem.faceItem.name,
                    id = faceItem.faceItem.id.toString(),
                    confidence = maxSimilarity.toFloat()
                )
            } else {
                null
            }
        }

        onUpdateList(resultList)
    } else {
        onUpdateList(emptyList())
    }
}

