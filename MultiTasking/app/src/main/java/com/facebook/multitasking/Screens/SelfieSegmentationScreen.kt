package com.facebook.multitasking.Screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.facebook.multitasking.FeatureScreenTemplate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelfieSegmentationScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var segmentationMask by remember { mutableStateOf<SegmentationMaskData?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var showSegmentation by remember { mutableStateOf(true) }
    var backgroundColorState by remember { mutableStateOf(androidx.compose.ui.graphics.Color(0xFF87CEEB)) } // Default sky blue
    var foregroundConfidenceThreshold by remember { mutableStateOf(0.9f) }

    // Cache for improved performance
    var cachedSegmentedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Define gradient colors for various UI elements
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(
            androidx.compose.ui.graphics.Color(0xFF2196F3),
            androidx.compose.ui.graphics.Color(0xFF1976D2)
        )
    )

    // Create a temporary file for the camera image
    val tempFile = remember {
        try {
            File.createTempFile("temp_image", ".jpg", context.cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    val tempUri = remember(tempFile) {
        tempFile?.let {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                it
            )
        }
    }

    // When background color or threshold changes, update the cached bitmap
    LaunchedEffect(backgroundColorState, foregroundConfidenceThreshold) {
        if (capturedBitmap != null && segmentationMask != null) {
            withContext(Dispatchers.Default) {
                val newBitmap = createSegmentedBitmap(
                    originalBitmap = capturedBitmap!!,
                    maskData = segmentationMask!!,
                    backgroundColor = backgroundColorState.toArgb(),
                    confidenceThreshold = foregroundConfidenceThreshold
                )
                cachedSegmentedBitmap = newBitmap
            }
        }
    }

    // Update the image capturing part
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempUri != null) {
            capturedImageUri = tempUri  // Don't create a new URI with timestamp here
            isProcessing = true
            cachedSegmentedBitmap = null  // Clear cache when taking new picture

            try {
                // Use coroutine for UI responsiveness
                lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val fullBitmap = withContext(Dispatchers.IO) {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, tempUri)
                    }

                    // Scale down bitmap for faster processing
                    val maxDimension = 1024
                    val scaleFactor = min(
                        maxDimension.toFloat() / fullBitmap.width,
                        maxDimension.toFloat() / fullBitmap.height
                    )

                    val scaledWidth = (fullBitmap.width * scaleFactor).toInt()
                    val scaledHeight = (fullBitmap.height * scaleFactor).toInt()
                    val scaledBitmap = Bitmap.createScaledBitmap(fullBitmap, scaledWidth, scaledHeight, true)

                    withContext(Dispatchers.Main) {
                        capturedBitmap = scaledBitmap
                    }

                    // Run segmentation in background
                    performSelfieSegmentation(scaledBitmap) { maskData ->
                        segmentationMask = maskData

                        // Pre-compute segmented bitmap
                        val segmentedBitmap = createSegmentedBitmap(
                            originalBitmap = scaledBitmap,
                            maskData = maskData,
                            backgroundColor = backgroundColorState.toArgb(),
                            confidenceThreshold = foregroundConfidenceThreshold
                        )

                        CoroutineScope(Dispatchers.IO).launch {
                            withContext(Dispatchers.Main) {
                                cachedSegmentedBitmap = segmentedBitmap
                                isProcessing = false
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                isProcessing = false
                segmentationMask = null
            }
        }
    }

// Update the display part
    if (capturedImageUri != null && capturedBitmap != null) {
        if (showSegmentation && cachedSegmentedBitmap != null) {
            // Use the cached segmented bitmap directly
            Image(
                bitmap = cachedSegmentedBitmap!!.asImageBitmap(),
                contentDescription = "Segmented image",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit  // Try ContentScale.Crop if fit doesn't work well
            )
        } else {
            // Show original image if segmentation is turned off
            Image(
                bitmap = capturedBitmap!!.asImageBitmap(),  // Use bitmap directly instead of AsyncImagePainter
                contentDescription = "Captured image",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }

    // Request camera permission
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted && tempUri != null) {
            takePictureLauncher.launch(tempUri)
        }
    }

    // Create color picker options
    val backgroundColorOptions = listOf(
        androidx.compose.ui.graphics.Color(0xFF87CEEB), // Sky Blue
        androidx.compose.ui.graphics.Color(0xFF4CAF50), // Green
        androidx.compose.ui.graphics.Color(0xFFE91E63), // Pink
        androidx.compose.ui.graphics.Color(0xFF9C27B0), // Purple
        androidx.compose.ui.graphics.Color(0xFF000000), // Black
        androidx.compose.ui.graphics.Color(0xFFFFFFFF), // White
    )

    FeatureScreenTemplate(
        title = "Selfie Segmentation",
        navController = navController
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header text
            Text(
                text = "Selfie Segmentation",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Image container with segmentation overlay
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Background color
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(backgroundColorState)
                    )

                    // Show placeholder or captured image with overlay
                    if (capturedImageUri != null && capturedBitmap != null) {
                        if (showSegmentation && cachedSegmentedBitmap != null) {
                            // Use the cached segmented bitmap directly
                            Image(
                                bitmap = cachedSegmentedBitmap!!.asImageBitmap(),
                                contentDescription = "Segmented image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Show original image if segmentation is turned off
                            val uniqueUri = Uri.parse("${capturedImageUri.toString()}?timestamp=${System.currentTimeMillis()}")
                            val painter = rememberAsyncImagePainter(uniqueUri)
                            Image(
                                painter = painter,
                                contentDescription = "Captured image",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // Add a subtle overlay gradient for better text visibility
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            androidx.compose.ui.graphics.Color.Transparent,
                                            androidx.compose.ui.graphics.Color(0x66000000)
                                        ),
                                        startY = 0f,
                                        endY = 900f
                                    )
                                )
                        )
                    } else {
                        // Placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color(0xFFEEEEEE)),
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
                                    tint = androidx.compose.ui.graphics.Color(0xFF9E9E9E)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No image captured yet",
                                    color = androidx.compose.ui.graphics.Color(0xFF757575),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // Show processing indicator
                    if (isProcessing) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    color = androidx.compose.ui.graphics.Color(0x88000000),
                                    shape = RoundedCornerShape(40.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = androidx.compose.ui.graphics.Color.White,
                                strokeWidth = 4.dp
                            )
                        }
                    }
                }
            }

            // Controls section
            if (segmentationMask != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Toggle segmentation
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Show Segmentation",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = showSegmentation,
                                onCheckedChange = { showSegmentation = it }
                            )
                        }

                        Divider()

                        // Background color selection
                        Text(
                            text = "Background Color",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            backgroundColorOptions.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(color, CircleShape)
                                        .border(
                                            BorderStroke(
                                                width = if (color == backgroundColorState) 3.dp else 1.dp,
                                                color = if (color == backgroundColorState)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    androidx.compose.ui.graphics.Color.Gray
                                            ),
                                            shape = CircleShape
                                        )
                                        .clickable { backgroundColorState = color }
                                )
                            }
                        }

                        Divider()

                        // Confidence threshold slider
                        Text(
                            text = "Foreground Threshold: ${String.format("%.2f", foregroundConfidenceThreshold)}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Slider(
                            value = foregroundConfidenceThreshold,
                            onValueChange = { foregroundConfidenceThreshold = it },
                            valueRange = 0.5f..0.95f,
                            steps = 9
                        )
                    }
                }
            }

            // Capture button with gradient
            Button(
                onClick = {
                    requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = androidx.compose.ui.graphics.Color.White
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
                            text = "Capture Selfie",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Segmentation info card
            if (segmentationMask != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFE0E0E0))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Segmentation Information",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // Segmentation details
                        InfoRow(
                            title = "Mask Size",
                            value = "${segmentationMask!!.width} × ${segmentationMask!!.height}",
                            icon = "📐"
                        )

                        InfoRow(
                            title = "Foreground %",
                            value = "${segmentationMask!!.foregroundPercentage.toInt()}%",
                            icon = "👤"
                        )

                        InfoRow(
                            title = "Detected People",
                            value = if (segmentationMask!!.foregroundPercentage > 5) "Yes" else "No",
                            icon = "🔍"
                        )
                    }
                }
            }

            // Show a help text when no faces are detected
            if (capturedImageUri != null && segmentationMask != null &&
                segmentationMask!!.foregroundPercentage < 5 && !isProcessing) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFFFF8E1)
                    )
                ) {
                    Text(
                        text = "No person detected in the image. Try capturing a selfie with a clear view of a person.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = androidx.compose.ui.graphics.Color(0xFF5D4037)
                    )
                }
            }
        }
    }
}

data class SegmentationMaskData(
    val buffer: ByteBuffer,
    val width: Int,
    val height: Int,
    val foregroundPercentage: Float
)

@Composable
fun InfoRow(title: String, value: String, icon: String) {
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
            modifier = Modifier.width(120.dp)
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

// Updated createSegmentedBitmap function
private fun createSegmentedBitmap(
    originalBitmap: Bitmap,
    maskData: SegmentationMaskData,
    backgroundColor: Int,
    confidenceThreshold: Float
): Bitmap {
    // Create a mutable copy of the original bitmap
    val resultBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val pixels = IntArray(resultBitmap.width * resultBitmap.height)

    // Get all pixels at once
    resultBitmap.getPixels(pixels, 0, resultBitmap.width, 0, 0, resultBitmap.width, resultBitmap.height)

    val maskWidth = maskData.width
    val maskHeight = maskData.height
    val buffer = maskData.buffer.duplicate() // Use duplicate to avoid affecting original buffer
    buffer.rewind() // Ensure we start from beginning

    // Calculate scaling factors
    val scaleX = resultBitmap.width.toFloat() / maskWidth
    val scaleY = resultBitmap.height.toFloat() / maskHeight

    // For each pixel in the result bitmap, find the corresponding mask pixel
    for (y in 0 until resultBitmap.height) {
        for (x in 0 until resultBitmap.width) {
            // Find corresponding coordinate in mask
            val maskX = (x / scaleX).toInt().coerceIn(0, maskWidth - 1)
            val maskY = (y / scaleY).toInt().coerceIn(0, maskHeight - 1)

            // Get the confidence value from buffer
            val pos = (maskY * maskWidth + maskX) * 4 // 4 bytes per float
            buffer.position(pos)
            val confidence = buffer.getFloat()

            // If below threshold, replace with background color
            // IMPORTANT: The inversion of the condition - this is a key fix
            if (confidence < confidenceThreshold) {
                pixels[y * resultBitmap.width + x] = backgroundColor
            }
        }
    }

    // Set all pixels back to the bitmap
    resultBitmap.setPixels(pixels, 0, resultBitmap.width, 0, 0, resultBitmap.width, resultBitmap.height)
    return resultBitmap
}

// Updated segmentation processing function
private fun performSelfieSegmentation(bitmap: Bitmap, onResult: (SegmentationMaskData) -> Unit) {
    val image = InputImage.fromBitmap(bitmap, 0)

    // Create options for the selfie segmenter - use SINGLE_IMAGE_MODE for better performance
    val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE) // Faster than STREAM_MODE
        .enableRawSizeMask()
        .build()

    val segmenter = Segmentation.getClient(options)

    segmenter.process(image)
        .addOnSuccessListener { segmentationMask ->
            // Get the raw buffer
            val buffer = segmentationMask.buffer
            val width = segmentationMask.width
            val height = segmentationMask.height

            // Calculate foreground percentage
            var foregroundPixels = 0
            val totalPixels = width * height
            buffer.rewind()

            for (i in 0 until totalPixels) {
                val confidence = buffer.getFloat()
                if (confidence > 0.5f) { // Lowered threshold for counting foreground pixels
                    foregroundPixels++
                }
            }

            val foregroundPercentage = (foregroundPixels.toFloat() / totalPixels) * 100

            // Reset buffer position
            buffer.rewind()

            // Create mask data object
            val maskData = SegmentationMaskData(
                buffer = buffer,
                width = width,
                height = height,
                foregroundPercentage = foregroundPercentage
            )

            onResult(maskData)
        }
        .addOnFailureListener { e ->
            e.printStackTrace()
            // Create empty mask data as fallback
            val emptyBuffer = ByteBuffer.allocate(4) // At least one float
            val emptyMask = SegmentationMaskData(
                buffer = emptyBuffer,
                width = 1,
                height = 1,
                foregroundPercentage = 0f
            )
            onResult(emptyMask)
        }
}

