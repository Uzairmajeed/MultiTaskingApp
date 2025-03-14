package com.facebook.multitasking.Screens

import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.facebook.multitasking.FeatureScreenTemplate
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class DigitalInkRecognitionResult(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val confidence: Float,
    val language: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class InkStroke(
    val points: List<Offset>,
    val strokeWidth: Float = 5f,
    val strokeColor: Color = Color.Black
)



@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DigitalInkRecognitionScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val TAG = "DigitalInkRecognition"

    // State for ink drawing
    var inkStrokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }
    var currentInkStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var isDrawing by remember { mutableStateOf(false) }

    // State for recognition results
    var recognitionResults by remember { mutableStateOf<List<DigitalInkRecognitionResult>>(emptyList()) }
    var currentRecognitionResult by remember { mutableStateOf<DigitalInkRecognitionResult?>(null) }
    var isRecognizing by remember { mutableStateOf(false) }

    // State for language selection
    var selectedLanguage by remember { mutableStateOf("en-US") }
    var isLanguageDownloaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    // Pre-context for better recognition
    var preContext by remember { mutableStateOf("") }

    // Available languages
    val availableLanguages = remember {
        listOf(
            "en-US" to "English (US)",
            "fr-FR" to "French",
            "de-DE" to "German",
            "es-ES" to "Spanish",
            "zh-CN" to "Chinese (Simplified)",
            "ja-JP" to "Japanese",
            "ko-KR" to "Korean",
            "ru-RU" to "Russian",
            "ar-SA" to "Arabic",
            "hi-IN" to "Hindi"
        )
    }

    // Check if the selected language model is downloaded
    val remoteModelManager = RemoteModelManager.getInstance()

    // Function to check if a language model is downloaded
    fun checkIfModelDownloaded(languageTag: String) {
        try {
            val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            if (modelIdentifier == null) {
                isLanguageDownloaded = false
                return
            }

            val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()

            remoteModelManager.isModelDownloaded(model)
                .addOnSuccessListener { downloaded ->
                    isLanguageDownloaded = downloaded
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking if model is downloaded: ${e.message}")
                    isLanguageDownloaded = false
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error identifying model: ${e.message}")
            isLanguageDownloaded = false
        }
    }

    // Function to download the selected language model
    fun downloadLanguageModel(languageTag: String) {
        try {
            val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            if (modelIdentifier == null) {
                return
            }

            val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
            isDownloading = true

            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()

            remoteModelManager.download(model, conditions)
                .addOnSuccessListener {
                    isLanguageDownloaded = true
                    isDownloading = false
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error downloading model: ${e.message}")
                    isDownloading = false
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading model: ${e.message}")
            isDownloading = false
        }
    }

    // Function to delete the downloaded language model
    fun deleteLanguageModel(languageTag: String) {
        try {
            val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(languageTag)
            if (modelIdentifier == null) {
                return
            }

            val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()

            remoteModelManager.deleteDownloadedModel(model)
                .addOnSuccessListener {
                    isLanguageDownloaded = false
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error deleting model: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model: ${e.message}")
        }
    }

    // Function to recognize digital ink
    fun recognizeInk() {
        if (inkStrokes.isEmpty()) {
            return
        }

        try {
            val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(selectedLanguage)
            if (modelIdentifier == null) {
                return
            }

            val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
            val recognizer = DigitalInkRecognition.getClient(
                DigitalInkRecognizerOptions.builder(model).build()
            )

            // Build the ink object
            val inkBuilder = Ink.builder()

            // Convert our custom InkStroke to ML Kit's Ink format
            for (stroke in inkStrokes) {
                val strokeBuilder = Ink.Stroke.builder()
                for (point in stroke.points) {
                    // In a real app, you'd want to capture the actual timestamps
                    strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, System.currentTimeMillis()))
                }
                inkBuilder.addStroke(strokeBuilder.build())
            }

            val ink = inkBuilder.build()

            // Set up recognition context with pre-context if available
            val recognitionContext = RecognitionContext.builder()
                .setPreContext(preContext)
                .setWritingArea(WritingArea(800f, 600f)) // Adjust to your canvas size
                .build()

            isRecognizing = true

            // Perform recognition
            recognizer.recognize(ink, recognitionContext)
                .addOnSuccessListener { result ->
                    isRecognizing = false

                    if (result.candidates.isNotEmpty()) {
                        val topCandidate = result.candidates[0]

                        // Fixed confidence calculation to handle null scores
                        val confidence = if (result.candidates.size > 1) {
                            val topScore = topCandidate.score
                            val secondScore = result.candidates[1].score

                            // Check if scores are null before calculating confidence
                            if (topScore != null && secondScore != null && topScore > 0) {
                                // Calculate confidence based on difference with second candidate
                                val ratio = secondScore / topScore
                                1.0f - ratio.coerceIn(0f, 1f)  // Ensure ratio is between 0 and 1
                            } else {
                                0.9f  // Default high confidence if scores are null or invalid
                            }
                        } else {
                            0.9f  // Default high confidence if there's only one candidate
                        }

                        val newResult = DigitalInkRecognitionResult(
                            text = topCandidate.text,
                            confidence = confidence,
                            language = selectedLanguage
                        )

                        recognitionResults = recognitionResults + newResult
                        currentRecognitionResult = newResult

                        // Update pre-context with the recognized text
                        preContext = if (preContext.length + topCandidate.text.length > 20) {
                            // Keep only the last 20 characters
                            (preContext + topCandidate.text).takeLast(20)
                        } else {
                            preContext + topCandidate.text
                        }
                    }
                }
                .addOnFailureListener { e ->
                    isRecognizing = false
                    Log.e(TAG, "Error recognizing ink: ${e.message}")
                }
        } catch (e: Exception) {
            isRecognizing = false
            Log.e(TAG, "Error setting up recognition: ${e.message}")
        }
    }

    // Check if the selected language model is downloaded on initialization
    LaunchedEffect(selectedLanguage) {
        checkIfModelDownloaded(selectedLanguage)
    }

    // UI Components
    FeatureScreenTemplate(
        title = "Digital Ink Recognition",
        navController = navController
    ) {
        // Main column with reduced padding to fit more content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp) // Reduced spacing
        ) {
            // Header text - made smaller
            Text(
                text = "Handwriting Recognition",
                fontSize = 20.sp, // Reduced from 24sp
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Language selection - more compact
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp), // Reduced from 16dp
                    verticalArrangement = Arrangement.spacedBy(6.dp) // Reduced from 8dp
                ) {
                    Text(
                        text = "Select Language",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, // Reduced from 16sp
                        color = MaterialTheme.colorScheme.primary
                    )

                    var expanded by remember { mutableStateOf(false) }
                    val selectedLanguageName = availableLanguages.find { it.first == selectedLanguage }?.second ?: "Unknown"

                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        TextField(
                            value = selectedLanguageName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(),
                            // More compact TextField
                            colors = TextFieldDefaults.textFieldColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableLanguages.forEach { (tag, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        selectedLanguage = tag
                                        expanded = false
                                        checkIfModelDownloaded(tag)
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isLanguageDownloaded) "Model Downloaded" else "Model Not Downloaded",
                            color = if (isLanguageDownloaded) Color(0xFF388E3C) else Color(0xFFD32F2F),
                            fontSize = 12.sp // Smaller text
                        )

                        Button(
                            onClick = {
                                if (isLanguageDownloaded) {
                                    deleteLanguageModel(selectedLanguage)
                                } else {
                                    downloadLanguageModel(selectedLanguage)
                                }
                            },
                            enabled = !isDownloading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLanguageDownloaded) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                            ),
                            // Smaller button
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = if (isLanguageDownloaded) "Delete Model" else "Download Model",
                                    fontSize = 12.sp // Smaller text
                                )
                            }
                        }
                    }
                }
            }

            // Drawing Canvas - slightly reduced height
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp) // Reduced from 300dp
                    .shadow(6.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Canvas for drawing - improved touch handling
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF5F5F5))
                            .pointerInteropFilter { event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        isDrawing = true
                                        currentInkStroke = listOf(Offset(event.x, event.y))
                                        true
                                    }

                                    MotionEvent.ACTION_MOVE -> {
                                        if (isDrawing) {
                                            currentInkStroke = currentInkStroke + Offset(event.x, event.y)
                                            true
                                        } else {
                                            false
                                        }
                                    }

                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        if (isDrawing) {
                                            isDrawing = false
                                            if (currentInkStroke.isNotEmpty()) {
                                                inkStrokes = inkStrokes + InkStroke(currentInkStroke)
                                                currentInkStroke = emptyList()
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }

                                    else -> false
                                }
                            }
                    ) {
                        // Draw saved strokes
                        for (stroke in inkStrokes) {
                            val path = Path()
                            if (stroke.points.isNotEmpty()) {
                                path.moveTo(stroke.points.first().x, stroke.points.first().y)
                                for (i in 1 until stroke.points.size) {
                                    path.lineTo(stroke.points[i].x, stroke.points[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = stroke.strokeColor,
                                style = Stroke(width = stroke.strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Draw current stroke
                        if (currentInkStroke.isNotEmpty()) {
                            val path = Path()
                            path.moveTo(currentInkStroke.first().x, currentInkStroke.first().y)
                            for (i in 1 until currentInkStroke.size) {
                                path.lineTo(currentInkStroke[i].x, currentInkStroke[i].y)
                            }
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(width = 5f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Show processing indicator
                    if (isRecognizing) {
                        Box(
                            modifier = Modifier
                                .size(60.dp) // Reduced from 80dp
                                .background(
                                    color = Color(0x88000000),
                                    shape = RoundedCornerShape(30.dp)
                                )
                                .align(Alignment.Center),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp), // Reduced from 48dp
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }
            }

            // Action buttons - more compact
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Reduced spacing
            ) {
                // Clear button
                Button(
                    onClick = {
                        inkStrokes = emptyList()
                        currentInkStroke = emptyList()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp), // Reduced from 56dp
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF5350)
                    ),
                    shape = RoundedCornerShape(23.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp) // More compact padding
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            modifier = Modifier.size(20.dp) // Reduced from 24dp
                        )
                        Spacer(modifier = Modifier.width(4.dp)) // Reduced from 8dp
                        Text(
                            text = "Clear",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp // Reduced from 16sp
                        )
                    }
                }

                // Recognize button with gradient
                Button(
                    onClick = {
                        if (isLanguageDownloaded && inkStrokes.isNotEmpty()) {
                            recognizeInk()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp), // Reduced from 56dp
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White
                    ),
                    enabled = isLanguageDownloaded && inkStrokes.isNotEmpty() && !isRecognizing,
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(23.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF2196F3),
                                        Color(0xFF0D47A1)
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
                                imageVector = Icons.Default.Check,
                                contentDescription = "Recognize",
                                modifier = Modifier.size(20.dp) // Reduced from 24dp
                            )
                            Spacer(modifier = Modifier.width(4.dp)) // Reduced from 8dp
                            Text(
                                text = "Recognize",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp // Reduced from 16sp
                            )
                        }
                    }
                }
            }

            // Create a fixed height box for results and history
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // This will take remaining space
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Display current recognition result - more compact
                    if (currentRecognitionResult != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(4.dp, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 3.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE3F2FD)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp) // Reduced from 16dp
                            ) {
                                Text(
                                    text = "Recognition Result",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp, // Reduced from 18sp
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp)) // Reduced from 8dp
                                Text(
                                    text = currentRecognitionResult!!.text,
                                    fontSize = 20.sp, // Reduced from 24sp
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp)) // Reduced from 4dp
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Confidence:",
                                        fontSize = 12.sp, // Reduced from 14sp
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.width(6.dp)) // Reduced from 8dp
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = getConfidenceColor(currentRecognitionResult!!.confidence),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp) // Reduced padding
                                    ) {
                                        Text(
                                            text = "${(currentRecognitionResult!!.confidence * 100).toInt()}%",
                                            fontSize = 12.sp, // Reduced from 14sp
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!isRecognizing && inkStrokes.isNotEmpty()) {
                        // Show a hint when there's ink but no recognition yet
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp), // Reduced from 16dp
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFF3E0)
                            )
                        ) {
                            Text(
                                text = if (isLanguageDownloaded)
                                    "Click 'Recognize' to identify the handwriting"
                                else
                                    "Download the language model to recognize handwriting",
                                modifier = Modifier.padding(12.dp), // Reduced from 16dp
                                textAlign = TextAlign.Center,
                                color = Color(0xFFE65100),
                                fontSize = 12.sp // Added smaller font size
                            )
                        }
                    } else if (!isRecognizing && inkStrokes.isEmpty() && recognitionResults.isEmpty()) {
                        // Show a help text when nothing is drawn
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp), // Reduced from 16dp
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFE3F2FD)
                            )
                        ) {
                            Text(
                                text = "Write something in the drawing area to recognize handwriting",
                                modifier = Modifier.padding(12.dp), // Reduced from 16dp
                                textAlign = TextAlign.Center,
                                color = Color(0xFF01579B),
                                fontSize = 12.sp // Added smaller font size
                            )
                        }
                    }

/*                    // Show recognition history - key improvement area
                    if (recognitionResults.isNotEmpty()) {
                        // History header with clear button inline
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recognition History",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp, // Reduced from 16sp
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Inline clear history button (only show if multiple results)
                            if (recognitionResults.size > 1) {
                                TextButton(
                                    onClick = { recognitionResults = emptyList() },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFFD32F2F)
                                    ),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear History",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Clear",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Scrollable history list
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f), // This will take remaining space in the column
                            verticalArrangement = Arrangement.spacedBy(6.dp) // Reduced from 8dp
                        ) {
                            items(recognitionResults.sortedByDescending { it.timestamp }) { result ->
                                RecognitionHistoryCard(
                                    result = result,
                                    isSelected = result.id == currentRecognitionResult?.id,
                                    onSelect = { currentRecognitionResult = result }
                                )
                            }
                        }
                    }*/
                }
            }
        }
    }
}

@Composable
fun RecognitionHistoryCard(
    result: DigitalInkRecognitionResult,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val cardBackgroundColor = if (isSelected)
        Color(0xFFE3F2FD) else Color(0xFFF5F5F5)

    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(result.timestamp))

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
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Text",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = result.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "${result.language} • $formattedDate",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Confidence indicator
            Box(
                modifier = Modifier
                    .background(
                        color = getConfidenceColor(result.confidence),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${(result.confidence * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}