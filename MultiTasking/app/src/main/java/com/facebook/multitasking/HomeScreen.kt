package com.facebook.multitasking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Face4
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.facebook.multitasking.Navigation.DigitalInkRecognitionScreen
import com.facebook.multitasking.Navigation.DocumentScannerScreen
import com.facebook.multitasking.Navigation.FaceMeshDetectionScreen
import com.facebook.multitasking.Navigation.FaceRecognitionScreen
import com.facebook.multitasking.Navigation.ImageProcessingScreen
import com.facebook.multitasking.Navigation.PoseDetectionScreen
import com.facebook.multitasking.Navigation.Screen
import com.facebook.multitasking.Navigation.SelfieSegmentationScreen
import com.facebook.multitasking.Navigation.TextRecognitionScreen
import com.facebook.multitasking.Navigation.route
import com.facebook.multitasking.Screens.DigitalInkRecognitionScreen


data class FeatureData(
    val title: String,
    val icon: ImageVector,
    val startColor: Color,
    val endColor: Color,
    val screen: Screen
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val features = listOf(
        FeatureData(
            title = "Text Recognition",
            icon = Icons.Default.TextFields,
            startColor = Color(0xFF43CEA2),
            endColor = Color(0xFF185A9D),
            screen = TextRecognitionScreen
        ),
        FeatureData(
            title = "Face Recognition",
            icon = Icons.Default.Face,
            startColor = Color(0xFFFF512F),
            endColor = Color(0xFFF09819),
            screen = FaceRecognitionScreen
        ),
        FeatureData(
            title = " Face Mesh Detection",
            icon = Icons.Default.Face4,
            startColor = Color(0xFFFF518F),
            endColor = Color(0xFFF0016),
            screen = FaceMeshDetectionScreen
    ),
        FeatureData(
            title = " Pose Detection",
            icon = Icons.Default.PeopleOutline,
            startColor = Color(0xFF614385),
            endColor = Color(0xFFF0016),
            screen = PoseDetectionScreen
        ),

        FeatureData(
            title = " Selfie Segmentation",
            icon = Icons.Default.PeopleOutline,
            startColor = Color(0xFF00B4DB),
            endColor = Color(0xFFF0016),
            screen = SelfieSegmentationScreen
        ),
        FeatureData(
            title = "Document Scanner",
            icon = Icons.Default.DocumentScanner,
            startColor = Color(0xFFFF5F6D),
            endColor = Color(0xFFFFC371),
            screen = DocumentScannerScreen
        ),
        FeatureData(
            title = "Image Processing",
            icon = Icons.Default.Image,
            startColor = Color(0xFF00B4DB),
            endColor = Color(0xFF0083B0),
            screen = ImageProcessingScreen
        ),

        FeatureData(
            title = "Ink Recognizable",
            icon = Icons.Default.Image,
            startColor = Color(0xFF614385),
            endColor = Color(0xFFF0016),
            screen = DigitalInkRecognitionScreen
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Multi-Tasking App") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(features) { feature ->
                    FeatureItem(
                        title = feature.title,
                        icon = feature.icon,
                        startColor = feature.startColor,
                        endColor = feature.endColor,
                        // Use the extension function to navigate
                        onStartClick = {
                            // Make sure you're using the correct navigation extension
                            navController.navigate(feature.screen.route())
                        }
                    )
                }
            }
        }
    }
}