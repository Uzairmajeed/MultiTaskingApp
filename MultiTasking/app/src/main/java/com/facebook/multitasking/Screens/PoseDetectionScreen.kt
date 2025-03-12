package com.facebook.multitasking.Screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.facebook.multitasking.FeatureScreenTemplate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class PoseLandmarkData(
    val type: Int,
    val position: Offset,
    val inFrameLikelihood: Float
)

data class DetectedPose(
    val id: Int,
    val landmarks: List<PoseLandmarkData>,
    val allLandmarksInFrame: Boolean
)


@Composable
fun PoseDetectionScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState() // Add scroll state for the entire screen

    var hasCameraPermission by remember { mutableStateOf(false) }
    var detectedPoses by remember { mutableStateOf<List<DetectedPose>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showPoseSkeleton by remember { mutableStateOf(true) }
    var isCameraActive by remember { mutableStateOf(false) }

    // New states for image capture and detection mode
    var isRealTimeMode by remember { mutableStateOf(true) }
    var capturedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAnalyzingCapturedImage by remember { mutableStateOf(false) }

    // Preview view for camera
    val previewView = remember { PreviewView(context) }

    // Current frame bitmap for drawing
    var currentFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Define gradient colors for UI elements
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF2196F3),
            Color(0xFF1976D2)
        )
    )

    // Create the pose detector with STREAM_MODE for real-time detection
    val poseDetector = remember {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .setPreferredHardwareConfigs(PoseDetectorOptions.STREAM_MODE) // Optimize for speed
            .build()
        PoseDetection.getClient(options)
    }

    // Create the pose detector with SINGLE_IMAGE_MODE for static image detection
    val staticPoseDetector = remember {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    // Executor for camera operations
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Request camera permission
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) {
            isCameraActive = true
        }
    }

    // Class to analyze camera frames
    class PoseAnalyzer(
        private val poseDetector: com.google.mlkit.vision.pose.PoseDetector,
        private val onDetectedPose: (List<DetectedPose>, Bitmap) -> Unit,
        private val setProcessing: (Boolean) -> Unit
    ) : ImageAnalysis.Analyzer {

        private var lastAnalyzedTimestamp = 0L
        private val minimumAnalysisIntervalMs = 200 // Analyze every 200ms for better performance

        @androidx.annotation.OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp >= minimumAnalysisIntervalMs) {
                lastAnalyzedTimestamp = currentTimestamp
                val mediaImage = imageProxy.image
                if (mediaImage != null) {
                    // Set processing state at the start of analysis
                    setProcessing(true)

                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    // Create a proper bitmap from the image for display
                    val bitmap = imageProxyToBitmap(imageProxy)

                    poseDetector.process(image)
                        .addOnSuccessListener { pose ->
                            if (pose.allPoseLandmarks.isNotEmpty()) {
                                // Calculate scale factors for coordinate mapping
                                val scaleX = previewView.width.toFloat() / imageProxy.width.toFloat()
                                val scaleY = previewView.height.toFloat() / imageProxy.height.toFloat()

                                val detectedPose = DetectedPose(
                                    id = 1,
                                    landmarks = pose.allPoseLandmarks.map { landmark ->
                                        // Map coordinates to preview view space
                                        PoseLandmarkData(
                                            type = landmark.landmarkType,
                                            position = Offset(
                                                landmark.position.x * scaleX,
                                                landmark.position.y * scaleY
                                            ),
                                            inFrameLikelihood = landmark.inFrameLikelihood
                                        )
                                    },
                                    allLandmarksInFrame = pose.allPoseLandmarks.all { it.inFrameLikelihood > 0.8f }
                                )
                                onDetectedPose(listOf(detectedPose), bitmap)
                            } else {
                                onDetectedPose(emptyList(), bitmap)
                            }
                            // Turn off processing state after analysis is complete
                            setProcessing(false)
                        }
                        .addOnFailureListener { e ->
                            e.printStackTrace()
                            onDetectedPose(emptyList(), bitmap)
                            setProcessing(false)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } else {
                    setProcessing(false)
                    imageProxy.close()
                }
            } else {
                imageProxy.close()
            }
        }

        // Proper implementation of bitmap conversion
        private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
            val buffer = imageProxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val yuvImage = YuvImage(
                bytes,
                ImageFormat.NV21,
                imageProxy.width,
                imageProxy.height,
                null
            )

            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                Rect(0, 0, imageProxy.width, imageProxy.height),
                100,
                out
            )
            val imageBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            // Rotate bitmap if needed based on image rotation
            val matrix = Matrix()
            matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
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
    }

    // Function to analyze the captured image
    fun analyzeCapturedImage(bitmap: Bitmap) {
        isAnalyzingCapturedImage = true
        detectedPoses = emptyList()

        // Create InputImage from bitmap
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Process the image with the static pose detector
        staticPoseDetector.process(inputImage)
            .addOnSuccessListener { pose ->
                if (pose.allPoseLandmarks.isNotEmpty()) {
                    // Calculate scale factors for coordinate mapping
                    val scaleX = previewView.width.toFloat() / bitmap.width.toFloat()
                    val scaleY = previewView.height.toFloat() / bitmap.height.toFloat()

                    val detectedPose = DetectedPose(
                        id = 1,
                        landmarks = pose.allPoseLandmarks.map { landmark ->
                            // Map coordinates to preview view space
                            PoseLandmarkData(
                                type = landmark.landmarkType,
                                position = Offset(
                                    landmark.position.x * scaleX,
                                    landmark.position.y * scaleY
                                ),
                                inFrameLikelihood = landmark.inFrameLikelihood
                            )
                        },
                        allLandmarksInFrame = pose.allPoseLandmarks.all { it.inFrameLikelihood > 0.8f }
                    )
                    detectedPoses = listOf(detectedPose)
                } else {
                    detectedPoses = emptyList()
                }
                isAnalyzingCapturedImage = false
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                detectedPoses = emptyList()
                isAnalyzingCapturedImage = false
            }
    }

    // Photo capture use case and callbacks
    var imageCapture: ImageCapture? = null

    // Setup camera when permission is granted and camera should be active
    LaunchedEffect(hasCameraPermission, isCameraActive, isRealTimeMode) {
        if (hasCameraPermission && isCameraActive) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val cameraProvider = suspendCoroutine<ProcessCameraProvider> { continuation ->
                cameraProviderFuture.addListener({
                    continuation.resume(cameraProviderFuture.get())
                }, ContextCompat.getMainExecutor(context))
            }

            try {
                // Unbind all use cases
                cameraProvider.unbindAll()

                // Create a preview use case
                val preview = androidx.camera.core.Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Initialize image capture use case for static mode
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setTargetResolution(Size(1280, 720)) // Set a higher resolution
                    .build()

                // Select back camera by default
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                if (isRealTimeMode) {
                    // Create an image analysis use case for real-time mode
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(
                        cameraExecutor,
                        PoseAnalyzer(
                            poseDetector,
                            onDetectedPose = { poses, bitmap ->
                                detectedPoses = poses
                                currentFrameBitmap = bitmap
                            },
                            setProcessing = { processing ->
                                isProcessing = processing
                            }
                        )
                    )

                    // Bind preview and image analysis for real-time mode
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } else {
                    // For static mode, only bind preview and image capture
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (!isCameraActive) {
            // Reset detected poses when camera is off
            detectedPoses = emptyList()
            isProcessing = false
            capturedImageBitmap = null
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    FeatureScreenTemplate(
        title = if (isRealTimeMode) "Pose Detection Stream" else "Static Pose Detection",
        navController = navController
    ) {
        // Make the entire content scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState) // Add vertical scroll
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header text
            Text(
                text = if (isRealTimeMode) "Real-time Human Pose Detection" else "Static Image Pose Detection",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Detection mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detection Mode:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Static",
                        fontSize = 14.sp,
                        color = if (!isRealTimeMode) MaterialTheme.colorScheme.primary else Color.Gray
                    )

                    Switch(
                        checked = isRealTimeMode,
                        onCheckedChange = {
                            isRealTimeMode = it
                            // Reset states when switching modes
                            detectedPoses = emptyList()
                            capturedImageBitmap = null
                        }
                    )

                    Text(
                        text = "Real-time",
                        fontSize = 14.sp,
                        color = if (isRealTimeMode) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            // Camera preview with pose overlay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission && isCameraActive) {
                        if (capturedImageBitmap != null && !isRealTimeMode) {
                            // Show captured image in static mode
                            Image(
                                bitmap = capturedImageBitmap!!.asImageBitmap(),
                                contentDescription = "Captured Image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Camera preview for both modes
                            AndroidView(
                                factory = { previewView },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Pose skeleton overlay (works for both captured image and real-time)
                        if (showPoseSkeleton && detectedPoses.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithCache {
                                        onDrawBehind {
                                            for (pose in detectedPoses) {
                                                val landmarks = pose.landmarks

                                                // Draw connections between landmarks
                                                val connections = listOf(
                                                    // Face
                                                    PoseLandmark.LEFT_EYE to PoseLandmark.LEFT_EYE_INNER,
                                                    PoseLandmark.LEFT_EYE to PoseLandmark.LEFT_EYE_OUTER,
                                                    PoseLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EYE_INNER,
                                                    PoseLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EYE_OUTER,
                                                    PoseLandmark.LEFT_EYE to PoseLandmark.NOSE,
                                                    PoseLandmark.RIGHT_EYE to PoseLandmark.NOSE,
                                                    // Upper body
                                                    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
                                                    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
                                                    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
                                                    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
                                                    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
                                                    // Torso
                                                    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
                                                    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
                                                    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
                                                    // Lower body
                                                    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
                                                    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
                                                    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
                                                    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE
                                                )

                                                for ((start, end) in connections) {
                                                    val startLandmark = landmarks.find { it.type == start }
                                                    val endLandmark = landmarks.find { it.type == end }

                                                    if (startLandmark != null && endLandmark != null) {
                                                        // Different colors for different body parts
                                                        val color = when {
                                                            start in PoseLandmark.LEFT_SHOULDER..PoseLandmark.RIGHT_HIP ->
                                                                Color(0xFFFF0000) // Torso (red)
                                                            start in PoseLandmark.LEFT_HIP..PoseLandmark.RIGHT_FOOT_INDEX ->
                                                                Color(0xFF00FF00) // Legs (green)
                                                            start in PoseLandmark.LEFT_SHOULDER..PoseLandmark.RIGHT_WRIST ->
                                                                Color(0xFF0000FF) // Arms (blue)
                                                            else ->
                                                                Color(0xFFFFFF00) // Face (yellow)
                                                        }

                                                        drawLine(
                                                            color = color,
                                                            start = startLandmark.position,
                                                            end = endLandmark.position,
                                                            strokeWidth = 5f
                                                        )
                                                    }
                                                }

                                                // Draw points for each landmark
                                                landmarks.forEach { landmark ->
                                                    // Different colors for different body parts
                                                    val color = when (landmark.type) {
                                                        in PoseLandmark.LEFT_SHOULDER..PoseLandmark.RIGHT_HIP ->
                                                            Color(0xFFFF0000) // Torso (red)
                                                        in PoseLandmark.LEFT_KNEE..PoseLandmark.RIGHT_FOOT_INDEX ->
                                                            Color(0xFF00FF00) // Legs (green)
                                                        in PoseLandmark.LEFT_ELBOW..PoseLandmark.RIGHT_PINKY ->
                                                            Color(0xFF0000FF) // Arms (blue)
                                                        in PoseLandmark.NOSE..PoseLandmark.RIGHT_MOUTH ->
                                                            Color(0xFFFFFF00) // Face (yellow)
                                                        else -> Color(0xFFFF00FF) // Others (magenta)
                                                    }

                                                    drawCircle(
                                                        color = color,
                                                        radius = 8f,
                                                        center = landmark.position
                                                    )
                                                }
                                            }
                                        }
                                    }
                            )
                        }

                        // Show pose count indicator
                        if (detectedPoses.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                                    .size(48.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${detectedPoses.size}",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Show loading indicator when analyzing static image
                        if (isAnalyzingCapturedImage) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x88000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    } else {
                        // Placeholder when camera is not active
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFEEEEEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Camera,
                                    contentDescription = "Camera",
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF9E9E9E)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Camera preview not active",
                                    color = Color(0xFF757575),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // Toggle skeleton button
            if (detectedPoses.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show pose skeleton:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showPoseSkeleton,
                        onCheckedChange = { showPoseSkeleton = it }
                    )
                }
            }

            // Camera control and capture buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Camera toggle button
                Button(
                    onClick = {
                        if (!hasCameraPermission) {
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                        } else {
                            isCameraActive = !isCameraActive
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(buttonGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = "Camera",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isCameraActive) "Stop Camera" else "Start Camera",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // Capture button for static mode
                if (!isRealTimeMode && isCameraActive && hasCameraPermission) {
                    Button(
                        onClick = {
                            val imageCapture = imageCapture ?: return@Button

                            // Create temporary file to store the image
                            val photoFile = File(
                                context.cacheDir,
                                "pose_detection_${System.currentTimeMillis()}.jpg"
                            )

                            // Output options
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                            // Take the picture
                            imageCapture.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        // Load the saved image as bitmap
                                        val savedUri = Uri.fromFile(photoFile)
                                        val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, savedUri)
                                        capturedImageBitmap = bitmap

                                        // Analyze the captured image
                                        analyzeCapturedImage(bitmap)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        Toast.makeText(
                                            context,
                                            "Error capturing image: ${exception.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF4CAF50),
                                            Color(0xFF388E3C)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Capture",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Capture Image",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            // Reset button for static mode
            if (!isRealTimeMode && capturedImageBitmap != null) {
                Button(
                    onClick = {
                        capturedImageBitmap = null
                        detectedPoses = emptyList()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5722))
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset Image",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Show detected pose details
            if (detectedPoses.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    ),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Detected Poses (${detectedPoses.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Changed from LazyColumn to Column with ForEach to work better with parent vertical scroll
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            detectedPoses.forEach { pose ->
                                PoseInfoCard(pose)
                            }
                        }
                    }
                }
            }

            // Show appropriate help text based on mode
            if (isCameraActive && detectedPoses.isEmpty() && !isAnalyzingCapturedImage) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF8E1)
                    )
                ) {
                    Text(
                        text = if (isRealTimeMode)
                            "No poses detected. Make sure a person is visible in the frame with good lighting."
                        else if (capturedImageBitmap == null)
                            "Tap 'Capture Image' to take a photo for pose detection."
                        else
                            "No poses detected in the captured image. Try again with a clearer pose.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF5D4037)
                    )
                }
            }

            // Add spacer at the bottom to ensure everything is visible after scrolling
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
@Composable
fun PoseInfoCard(pose: DetectedPose) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFFE0E0E0),
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pose #${pose.id}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = if (pose.allLandmarksInFrame) "All landmarks in frame" else "Some landmarks out of frame",
                    fontSize = 12.sp,
                    color = if (pose.allLandmarksInFrame) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color(0xFFE0E0E0))
            Spacer(modifier = Modifier.height(8.dp))

            // Pose attributes
            PoseAttributeRow(
                title = "Total Landmarks",
                value = pose.landmarks.size.toString(),
                icon = "👤"
            )

            // Count landmarks by region
            val faceLandmarks = pose.landmarks.count { it.type in PoseLandmark.NOSE..PoseLandmark.RIGHT_MOUTH }
            val upperBodyLandmarks = pose.landmarks.count {
                it.type in PoseLandmark.LEFT_SHOULDER..PoseLandmark.RIGHT_WRIST
            }
            val lowerBodyLandmarks = pose.landmarks.count {
                it.type in PoseLandmark.LEFT_HIP..PoseLandmark.RIGHT_FOOT_INDEX
            }

            PoseAttributeRow(
                title = "Face Points",
                value = faceLandmarks.toString(),
                icon = "😊"
            )

            PoseAttributeRow(
                title = "Upper Body",
                value = upperBodyLandmarks.toString(),
                icon = "👕"
            )

            PoseAttributeRow(
                title = "Lower Body",
                value = lowerBodyLandmarks.toString(),
                icon = "👖"
            )

            // Calculate confidence
            val avgConfidence = pose.landmarks.map { it.inFrameLikelihood }.average()

            PoseAttributeRow(
                title = "Confidence",
                value = String.format("%.2f%%", avgConfidence * 100),
                icon = "📊"
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Key Positions",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            // Show some key position info
            val nose = pose.landmarks.find { it.type == PoseLandmark.NOSE }
            val leftShoulder = pose.landmarks.find { it.type == PoseLandmark.LEFT_SHOULDER }
            val rightShoulder = pose.landmarks.find { it.type == PoseLandmark.RIGHT_SHOULDER }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                nose?.let {
                    PositionInfo("Nose: (${it.position.x.toInt()},${it.position.y.toInt()})")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                leftShoulder?.let {
                    PositionInfo("L.Shoulder: (${it.position.x.toInt()},${it.position.y.toInt()})")
                }

                rightShoulder?.let {
                    PositionInfo("R.Shoulder: (${it.position.x.toInt()},${it.position.y.toInt()})")
                }
            }
        }
    }
}

@Composable
fun PoseAttributeRow(title: String, value: String, icon: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 8.dp)
        )

        Text(
            text = title,
            fontSize = 14.sp,
            modifier = Modifier.width(100.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = value,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 4.dp),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun PositionInfo(text: String) {
    Box(
        modifier = Modifier
            .border(
                width = 1.dp,
                color = Color(0xFFE0E0E0),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color(0xFF616161)
        )
    }
}