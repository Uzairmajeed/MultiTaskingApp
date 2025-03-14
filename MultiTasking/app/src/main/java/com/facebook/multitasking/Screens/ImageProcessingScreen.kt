package com.facebook.multitasking.Screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
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
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.facebook.multitasking.FeatureScreenTemplate
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

data class LabeledImage(
    val id: Long = System.currentTimeMillis(),
    val imageUri: Uri,
    val labels: List<ImageLabelInfo> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class ImageLabelInfo(
    val text: String,
    val confidence: Float,
    val index: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageProcessingScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State variables
    var labeledImages by remember { mutableStateOf<List<LabeledImage>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<LabeledImage?>(null) }
    var confidenceThreshold by remember { mutableStateOf(0.7f) }

    // First, create a URI for storing the captured image
    var currentPhotoUri by remember { mutableStateOf<Uri?>(null) }

    // Define gradient colors for UI elements
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF2196F3),
            Color(0xFF0D47A1)
        )
    )

    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE3F2FD),
            Color(0xFFBBDEFB)
        )
    )

    // Configure Image Labeler options
    val options = remember {
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(confidenceThreshold)
            .build()
    }

    // Get image labeler instance
    val labeler = remember { ImageLabeling.getClient(options) }

    // Function to process the image with ML Kit - defined as a val before it's used
    val processImage = { imageUri: Uri ->
        isProcessing = true

        try {
            val image = InputImage.fromFilePath(context, imageUri)

            labeler.process(image)
                .addOnSuccessListener { labels ->
                    // Convert ML Kit labels to our data model
                    val imageLabels = labels.map { label ->
                        ImageLabelInfo(
                            text = label.text,
                            confidence = label.confidence,
                            index = label.index
                        )
                    }

                    // Create new labeled image
                    val newLabeledImage = LabeledImage(
                        imageUri = imageUri,
                        labels = imageLabels
                    )

                    // Update state with new image
                    labeledImages = labeledImages + newLabeledImage
                    selectedImage = newLabeledImage

                    // Show success message
                    Toast.makeText(
                        context,
                        "Image labeled successfully: ${imageLabels.size} labels detected",
                        Toast.LENGTH_SHORT
                    ).show()

                    isProcessing = false
                }
                .addOnFailureListener { e ->
                    isProcessing = false
                    Toast.makeText(
                        context,
                        "Failed to process image: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        } catch (e: IOException) {
            isProcessing = false
            Toast.makeText(
                context,
                "Error loading image: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Setup activity result launcher for image picking
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { imageUri ->
            processImage(imageUri)
        }
    }

    // Setup activity result launcher for camera with file creation
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            // Process the saved image at currentPhotoUri
            processImage(currentPhotoUri!!)
        }
    }

    // Function to create a temporary image file and get its URI
    fun createImageUri(): Uri? {
        return try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "image_${System.currentTimeMillis()}.jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to create image file: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    // Function to pick image from gallery
    fun pickImageFromGallery() {
        galleryLauncher.launch("image/*")
    }


    // Updated function to take picture from camera
    fun takePicture() {
        currentPhotoUri = createImageUri()
        currentPhotoUri?.let { uri ->
            cameraLauncher.launch(uri)
        } ?: run {
            Toast.makeText(context, "Unable to create image file", Toast.LENGTH_SHORT).show()
        }
    }

    FeatureScreenTemplate(
        title = "Image Labeling",
        navController = navController
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header text
            Text(
                text = "Identify Objects in Images",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Image preview or placeholder
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Show placeholder or selected image preview
                    if (selectedImage != null) {
                        // Display the selected image
                        val painter = rememberAsyncImagePainter(selectedImage!!.imageUri)

                        Image(
                            painter = painter,
                            contentDescription = "Image preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )

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

                        // Show label count indicator
                        if (selectedImage!!.labels.isNotEmpty()) {
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
                                    text = "${selectedImage!!.labels.size}",
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
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Image Labeling",
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF9E9E9E)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No image selected",
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

            // Row for image source buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Gallery button
                Button(
                    onClick = { pickImageFromGallery() },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Gallery",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gallery",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Camera button with gradient
                Button(
                    onClick = { takePicture() },
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
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Camera",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Camera",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // Show labels for selected image
            if (selectedImage != null && selectedImage!!.labels.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Detected Labels (${selectedImage!!.labels.size})",
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
                                .height(200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(selectedImage!!.labels.sortedByDescending { it.confidence }) { label ->
                                LabelCard(label = label)
                            }
                        }
                    }
                }
            } else if (selectedImage != null && selectedImage!!.labels.isEmpty()) {
                // Show a message when no labels were detected
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    )
                ) {
                    Text(
                        text = "No labels detected. Try adjusting the confidence threshold or try a different image.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = Color(0xFFE65100)
                    )
                }
            } else if (!isProcessing && labeledImages.isEmpty()) {
                // Show a help text when no images are processed
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD)
                    )
                ) {
                    Text(
                        text = "Select an image from gallery or take a photo to identify objects in the image.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF01579B)
                    )
                }
            }

            // Show history of processed images
            if (labeledImages.isNotEmpty()) {
                Text(
                    text = "Recently Processed Images",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(labeledImages.sortedByDescending { it.timestamp }) { image ->
                        HistoryImageCard(
                            labeledImage = image,
                            isSelected = image.id == selectedImage?.id,
                            onSelect = { selectedImage = image }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LabelCard(label: ImageLabelInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Label,
                    contentDescription = "Label",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label.text,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Confidence percentage
            Box(
                modifier = Modifier
                    .background(
                        color = getConfidenceColor(label.confidence),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${(label.confidence * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun HistoryImageCard(
    labeledImage: LabeledImage,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val cardBackgroundColor = if (isSelected)
        Color(0xFFE3F2FD) else Color(0xFFF5F5F5)

    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(labeledImage.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Image(
                painter = rememberAsyncImagePainter(labeledImage.imageUri),
                contentDescription = "Thumbnail",
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Image #${labeledImage.id.toString().takeLast(4)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Labels: ${labeledImage.labels.size} • $formattedDate",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                if (labeledImage.labels.isNotEmpty()) {
                    val topLabels = labeledImage.labels
                        .sortedByDescending { it.confidence }
                        .take(2)
                        .joinToString(", ") { it.text }

                    Text(
                        text = "Top: $topLabels",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// Helper function to get color based on confidence
fun getConfidenceColor(confidence: Float): Color {
    return when {
        confidence >= 0.8f -> Color(0xFF388E3C) // High confidence - Green
        confidence >= 0.6f -> Color(0xFF689F38) // Good confidence - Light Green
        confidence >= 0.4f -> Color(0xFFFFA000) // Medium confidence - Amber
        else -> Color(0xFFFF7043) // Low confidence - Deep Orange
    }
}