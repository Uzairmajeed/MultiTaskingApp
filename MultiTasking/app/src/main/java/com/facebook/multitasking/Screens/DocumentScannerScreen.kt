package com.facebook.multitasking.Screens

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.PictureAsPdf
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
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import kotlinx.coroutines.launch

data class ScannedDocument(
    val id: Long = System.currentTimeMillis(),
    val imageUris: List<Uri> = emptyList(),
    val pdfUri: Uri? = null,
    val pageCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State variables
    var scannedDocuments by remember { mutableStateOf<List<ScannedDocument>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedDocument by remember { mutableStateOf<ScannedDocument?>(null) }

    // Define gradient colors for UI elements
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF4CAF50),
            Color(0xFF2E7D32)
        )
    )

    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFE0E0E0),
            Color(0xFFF5F5F5)
        )
    )

    // Configure Document Scanner options
    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
    }

    // Get document scanner instance
    val scanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    // Setup activity result launcher for document scanning
    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        isScanning = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

            // Process scanning result
            scanningResult?.let { scanResult ->
                val imageUris = mutableListOf<Uri>()
                var pdfUri: Uri? = null
                var pageCount = 0

                // Get image URIs
                scanResult.getPages()?.let { pages ->
                    for (page in pages) {
                        page.getImageUri()?.let { imageUris.add(it) }
                    }
                }

                // Get PDF URI
                scanResult.getPdf()?.let { pdf ->
                    pdfUri = pdf.getUri()
                    pageCount = pdf.getPageCount()
                }

                if (imageUris.isNotEmpty() || pdfUri != null) {
                    val newDocument = ScannedDocument(
                        imageUris = imageUris,
                        pdfUri = pdfUri,
                        pageCount = pageCount
                    )
                    scannedDocuments = scannedDocuments + newDocument

                    // Show success message
                    Toast.makeText(
                        context,
                        "Document scanned successfully: ${imageUris.size} pages",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Function to start document scanning
    fun startDocumentScanner() {
        isScanning = true
        scanner.getStartScanIntent(context as Activity)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { exception ->
                isScanning = false
                Toast.makeText(
                    context,
                    "Failed to start scanner: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    FeatureScreenTemplate(
        title = "Document Scanner",
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
                text = "Scan & Manage Documents",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Document preview or placeholder
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
                    // Show placeholder or selected document preview
                    if (selectedDocument != null && selectedDocument!!.imageUris.isNotEmpty()) {
                        // Display the first page of selected document
                        val previewUri = selectedDocument!!.imageUris.first()
                        val painter = rememberAsyncImagePainter(previewUri)

                        Image(
                            painter = painter,
                            contentDescription = "Document preview",
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

                        // Show page count indicator
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
                                text = "${selectedDocument!!.imageUris.size}",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = "Document Scanner",
                                    modifier = Modifier.size(64.dp),
                                    tint = Color(0xFF9E9E9E)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No document selected",
                                    color = Color(0xFF757575),
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // Show scanning indicator
                    if (isScanning) {
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

            // Scan button with gradient
            Button(
                onClick = { startDocumentScanner() },
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
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "Scanner",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scan New Document",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Document list
            if (scannedDocuments.isNotEmpty()) {
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
                            text = "Your Documents (${scannedDocuments.size})",
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
                            items(scannedDocuments) { document ->
                                DocumentCard(
                                    document = document,
                                    isSelected = document.id == selectedDocument?.id,
                                    onSelect = { selectedDocument = document },
                                    onDelete = {
                                        scannedDocuments = scannedDocuments.filter { it.id != document.id }
                                        if (selectedDocument?.id == document.id) {
                                            selectedDocument = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else if (!isScanning) {
                // Show a help text when no documents are available
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    )
                ) {
                    Text(
                        text = "Tap the 'Scan New Document' button to digitize your physical documents.",
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF33691E)
                    )
                }
            }
        }
    }
}

@Composable
fun DocumentCard(
    document: ScannedDocument,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBackgroundColor = if (isSelected)
        Color(0xFFE8F5E9) else Color(0xFFF5F5F5)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color(0xFF4CAF50) else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onSelect), // This is now using the built-in clickable modifier from Compose
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackgroundColor
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (document.pdfUri != null)
                            Icons.Default.PictureAsPdf else Icons.Default.FilePresent,
                        contentDescription = "Document Type",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Document #${document.id.toString().takeLast(4)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Pages: ${document.imageUris.size}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Document info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DocumentInfoChip(
                    label = if (document.pdfUri != null) "PDF Available" else "Images Only",
                    backgroundColor = if (document.pdfUri != null)
                        Color(0xFFE3F2FD) else Color(0xFFF3E5F5)
                )

                DocumentInfoChip(
                    label = "Scanned ${getTimeAgo(document.timestamp)}",
                    backgroundColor = Color(0xFFFFF8E1)
                )
            }
        }
    }
}

@Composable
fun DocumentInfoChip(
    label: String,
    backgroundColor: Color
) {
    Box(
        modifier = Modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF616161)
        )
    }
}

// Helper function to format timestamp
fun getTimeAgo(timestamp: Long): String {
    val currentTime = System.currentTimeMillis()
    val diff = currentTime - timestamp

    return when {
        diff < 60 * 1000 -> "just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
        else -> "${diff / (24 * 60 * 60 * 1000)} days ago"
    }
}

// REMOVED the custom clickable extension function that was causing the crash