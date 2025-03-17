package com.facebook.multitasking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Face4
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import com.facebook.multitasking.Navigation.ARModelScreen
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
import com.facebook.multitasking.ui.theme.onTertiaryContainerLight
import com.facebook.multitasking.ui.theme.primaryDark
import com.facebook.multitasking.ui.theme.primaryLight

data class FeatureData(
    val title: String,
    val icon: ImageVector,
    val startColor: Color,
    val endColor: Color,
    val screen: Screen
)

@Composable
fun HomeScreen(navController: NavController) {
    // Define primary application colors for gradient background
    val backgroundGradientStart = onTertiaryContainerLight
    val backgroundGradientEnd = onTertiaryContainerLight


    // Define feature cards with refined color combinations
    val features = listOf(
        FeatureData(
            title = "Text Recognition",
            icon = Icons.Default.TextFields,
            startColor = Color(0xFF4CC9F0),
            endColor = Color(0xFF4361EE),
            screen = TextRecognitionScreen
        ),
        FeatureData(
            title = "Face Recognition",
            icon = Icons.Default.Face,
            startColor = Color(0xFFF72585),
            endColor = Color(0xFF7209B7),
            screen = FaceRecognitionScreen
        ),
        FeatureData(
            title = "Face Mesh Detection",
            icon = Icons.Default.Face4,
            startColor = Color(0xFF3A0CA3),
            endColor = Color(0xFF4361EE),
            screen = FaceMeshDetectionScreen
        ),
        FeatureData(
            title = "Pose Detection",
            icon = Icons.Default.PeopleOutline,
            startColor = Color(0xFF4CC9F0),
            endColor = Color(0xFF3A0CA3),
            screen = PoseDetectionScreen
        ),
        FeatureData(
            title = "Selfie Segmentation",
            icon = Icons.Default.PeopleOutline,
            startColor = Color(0xFF7209B7),
            endColor = Color(0xFFF72585),
            screen = SelfieSegmentationScreen
        ),
        FeatureData(
            title = "Document Scanner",
            icon = Icons.Default.DocumentScanner,
            startColor = Color(0xFFFF9E00),
            endColor = Color(0xFFFF0054),
            screen = DocumentScannerScreen
        ),
        FeatureData(
            title = "Image Processing",
            icon = Icons.Default.Image,
            startColor = Color(0xFF06D6A0),
            endColor = Color(0xFF1B9AAA),
            screen = ImageProcessingScreen
        ),
        FeatureData(
            title = "Ink Recognition",
            icon = Icons.Default.Image,
            startColor = Color(0xFF7678ED),
            endColor = Color(0xFF3A0CA3),
            screen = DigitalInkRecognitionScreen
        ),

        // NEW 3D MODEL SCREEN
        FeatureData(
            title = "3D Model Viewer",
            icon = Icons.Default.Image,
            startColor = Color(0xFFFF9E00),
            endColor = Color(0xFF38252B),
            screen = ARModelScreen
        )
    )

    // The main layout with a gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(backgroundGradientStart, backgroundGradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
        ) {
            // Header section with welcome message
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Column {
                    Text(
                        text = "Welcome to",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "AI-Powered Tools",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Card grid with feature items
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(features) { feature ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        FeatureItem(
                            title = feature.title.trim(), // Trim to remove leading spaces
                            icon = feature.icon,
                            startColor = feature.startColor,
                            endColor = feature.endColor,
                            onStartClick = {
                                navController.navigate(feature.screen.route())
                            }
                        )
                    }
                }
            }
        }
    }
}

