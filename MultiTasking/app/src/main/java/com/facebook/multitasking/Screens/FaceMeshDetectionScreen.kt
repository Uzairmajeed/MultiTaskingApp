package com.facebook.multitasking.Screens

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.facebook.multitasking.FeatureScreenTemplate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshPoint
import java.io.File
import java.io.IOException
import androidx.compose.ui.geometry.Offset

import androidx.compose.runtime.remember

import androidx.compose.ui.draw.drawWithCache
import android.graphics.Rect
import com.google.mlkit.vision.common.Triangle

data class MeshFace(
    val id: Int,
    val boundingBox: Rect,
    val meshPoints: List<FaceMeshPoint>,
    val triangles: List<Triangle<FaceMeshPoint>>  // Add the type parameter FaceMeshPoint
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceMeshDetectionScreen(navController: NavController) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectedMeshFaces by remember { mutableStateOf<List<MeshFace>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showMesh by remember { mutableStateOf(true) }

    // Define gradient colors for various UI elements
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF2196F3),
            Color(0xFF1976D2)
        )
    )

    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE0E0E0),
            Color(0xFFF5F5F5)
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

    // Take picture launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempUri != null) {
            val uniqueUri = Uri.parse("${tempUri.toString()}?timestamp=${System.currentTimeMillis()}")
            capturedImageUri = uniqueUri
            isProcessing = true

            try {
                val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, tempUri)
                capturedBitmap = bitmap
                detectFaceMesh(bitmap) { faces ->
                    detectedMeshFaces = faces
                    isProcessing = false
                }
            } catch (e: IOException) {
                e.printStackTrace()
                isProcessing = false
                detectedMeshFaces = emptyList()
            }
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

    FeatureScreenTemplate(
        title = "Face Mesh Detection",
        navController = navController
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header text
            Text(
                text = "Face Mesh Detection",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Image container with mesh overlay
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
                    // Show placeholder or captured image with overlay
                    if (capturedImageUri != null && capturedBitmap != null) {
                        val uniqueUri = Uri.parse("${capturedImageUri.toString()}?timestamp=${System.currentTimeMillis()}")
                        val painter = rememberAsyncImagePainter(uniqueUri)

                        // Base image
                        Image(
                            painter = painter,
                            contentDescription = "Captured image",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )

                        // Face mesh overlay if we have faces and showMesh is enabled
                        if (showMesh && detectedMeshFaces.isNotEmpty() && capturedBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithCache {
                                        onDrawBehind {
                                            // Draw triangles and points for each detected face
                                            for (face in detectedMeshFaces) {
                                                // Draw triangles first
                                                for (triangle in face.triangles) {
                                                    val points = triangle.allPoints
                                                    if (points.size == 3) {
                                                        // Calculate normalization values based on the bounding box
                                                        val imageWidth = capturedBitmap!!.width.toFloat()
                                                        val imageHeight = capturedBitmap!!.height.toFloat()

                                                        val canvasWidth = size.width
                                                        val canvasHeight = size.height

                                                        val scaleX = canvasWidth / imageWidth
                                                        val scaleY = canvasHeight / imageHeight
                                                        val scale = minOf(scaleX, scaleY)

                                                        // Adjust offset for centering
                                                        val offsetX = (canvasWidth - imageWidth * scale) / 2
                                                        val offsetY = (canvasHeight - imageHeight * scale) / 2

                                                        // Calculate transformed points
                                                        val p1 = Offset(
                                                            offsetX + points[0].position.x * scale,
                                                            offsetY + points[0].position.y * scale
                                                        )
                                                        val p2 = Offset(
                                                            offsetX + points[1].position.x * scale,
                                                            offsetY + points[1].position.y * scale
                                                        )
                                                        val p3 = Offset(
                                                            offsetX + points[2].position.x * scale,
                                                            offsetY + points[2].position.y * scale
                                                        )

                                                        // Draw triangle outline
                                                        drawLine(
                                                            Color(0x8000FF00),
                                                            p1,
                                                            p2,
                                                            strokeWidth = 0.5f
                                                        )
                                                        drawLine(
                                                            Color(0x8000FF00),
                                                            p2,
                                                            p3,
                                                            strokeWidth = 0.5f
                                                        )
                                                        drawLine(
                                                            Color(0x8000FF00),
                                                            p3,
                                                            p1,
                                                            strokeWidth = 0.5f
                                                        )
                                                    }
                                                }

                                                // Draw key points - just a subset for visibility
                                                val keyPoints = face.meshPoints.filterIndexed { index, _ ->
                                                    // Select a subset of points to display (e.g., every 10th point)
                                                    index % 10 == 0
                                                }

                                                for (point in keyPoints) {
                                                    val imageWidth = capturedBitmap!!.width.toFloat()
                                                    val imageHeight = capturedBitmap!!.height.toFloat()

                                                    val canvasWidth = size.width
                                                    val canvasHeight = size.height

                                                    val scaleX = canvasWidth / imageWidth
                                                    val scaleY = canvasHeight / imageHeight
                                                    val scale = minOf(scaleX, scaleY)

                                                    // Adjust offset for centering
                                                    val offsetX = (canvasWidth - imageWidth * scale) / 2
                                                    val offsetY = (canvasHeight - imageHeight * scale) / 2

                                                    val p = Offset(
                                                        offsetX + point.position.x * scale,
                                                        offsetY + point.position.y * scale
                                                    )

                                                    drawCircle(
                                                        Color(0xFFFF0000),
                                                        radius = 2f,
                                                        center = p
                                                    )
                                                }
                                            }
                                        }
                                    }
                            )
                        }

                        // Add a subtle overlay gradient for better text visibility
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0x66000000)
                                        ),
                                        startY = 0f,
                                        endY = 900f
                                    )
                                )
                        )

                        // Show face count indicator if faces were detected
                        if (detectedMeshFaces.isNotEmpty()) {
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
                                    text = "${detectedMeshFaces.size}",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        // Placeholder
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
                                    text = "No image captured yet",
                                    color = Color(0xFF757575),
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
                                    color = Color(0x88000000),
                                    shape = RoundedCornerShape(40.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = Color.White,
                                strokeWidth = 4.dp
                            )
                        }
                    }
                }
            }

            // Toggle mesh button
            if (detectedMeshFaces.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show mesh:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = showMesh,
                        onCheckedChange = { showMesh = it }
                    )
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
                            text = "Capture Image",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Show detected face mesh details
            if (detectedMeshFaces.isNotEmpty()) {
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
                            text = "Detected Face Mesh (${detectedMeshFaces.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(detectedMeshFaces) { faceMesh ->
                                FaceMeshInfoCard(faceMesh)
                            }
                        }
                    }
                }
            }

            // Show a help text when no faces are detected
            if (capturedImageUri != null && detectedMeshFaces.isEmpty() && !isProcessing) {
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
                        text = "No face mesh detected in the image. Try capturing a clearer image with visible faces within 2 meters of the camera.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF5D4037)
                    )
                }
            }
        }
    }
}

@Composable
fun FaceMeshInfoCard(faceMesh: MeshFace) {
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
                    text = "Face Mesh #${faceMesh.id}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color(0xFFE0E0E0))
            Spacer(modifier = Modifier.height(8.dp))

            // Face mesh attributes
            MeshAttributeRow(
                title = "Total Points",
                value = faceMesh.meshPoints.size.toString(),
                icon = "👤"
            )

            MeshAttributeRow(
                title = "Triangles",
                value = faceMesh.triangles.size.toString(),
                icon = "▲"
            )

            // Z-depth info
            val zValues = faceMesh.meshPoints.map { it.position.z }
            val avgDepth = zValues.average()

            MeshAttributeRow(
                title = "Avg Z-Depth",
                value = String.format("%.2f", avgDepth),
                icon = "📏"
            )

            // Bounding box info
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Bounding Box",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BoundingBoxInfo("Left: ${faceMesh.boundingBox.left}")
                BoundingBoxInfo("Top: ${faceMesh.boundingBox.top}")
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BoundingBoxInfo("Right: ${faceMesh.boundingBox.right}")
                BoundingBoxInfo("Bottom: ${faceMesh.boundingBox.bottom}")
            }
        }
    }
}

@Composable
fun MeshAttributeRow(title: String, value: String, icon: String) {
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
fun BoundingBoxInfo(text: String) {
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

private fun detectFaceMesh(bitmap: Bitmap, onResult: (List<MeshFace>) -> Unit) {
    val image = InputImage.fromBitmap(bitmap, 0)

    // Create options for the face mesh detector
    val options = FaceMeshDetectorOptions.Builder()
        .setUseCase(FaceMeshDetectorOptions.FACE_MESH)  // Corrected method name and constant
        .build()

    val detector = FaceMeshDetection.getClient(options)

    detector.process(image)
        .addOnSuccessListener { faceMeshes ->
            // Convert to our data model
            val detectedMeshFaces = faceMeshes.mapIndexed { index, faceMesh ->
                MeshFace(
                    id = index + 1,
                    boundingBox = faceMesh.boundingBox,
                    meshPoints = faceMesh.allPoints,
                    triangles = faceMesh.allTriangles  // Use 'triangles' instead of 'allTriangles'
                )
            }
            onResult(detectedMeshFaces)
        }
        .addOnFailureListener { e ->
            e.printStackTrace()
            onResult(emptyList())
        }
}