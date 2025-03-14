package com.facebook.multitasking.Navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
// Function to get route string from Screen object
fun Screen.route(): String {
    return when (this) {
        is HomeScreen -> "home"
        is TextRecognitionScreen -> "text_recognition"
        is FaceRecognitionScreen -> "face_recognition"
        is FaceMeshDetectionScreen -> "face_mesh_detection"
        is PoseDetectionScreen -> "pose_detection"
        is SelfieSegmentationScreen -> " selfie_segmentation"
        is DocumentScannerScreen -> "document_scanner"
        is ImageProcessingScreen -> "image_processing"
        is DigitalInkRecognitionScreen-> "ink_recognitions"
    }
}

// Extension function to navigate using Screen objects
fun NavController.navigate(screen: Screen) {
    val route = screen.route()
    this.navigate(route)
}

inline fun <reified T : Screen> NavGraphBuilder.composable(
    noinline content: @Composable (NavBackStackEntry) -> Unit
) {
    val route = when (T::class) {
        HomeScreen::class -> "home"
        TextRecognitionScreen::class -> "text_recognition"
        FaceRecognitionScreen::class -> "face_recognition"
        FaceMeshDetectionScreen::class -> "face_mesh_detection"
        PoseDetectionScreen::class -> "pose_detection"
        SelfieSegmentationScreen::class -> " selfie_segmentation"
        DocumentScannerScreen::class -> "document_scanner"
        ImageProcessingScreen::class -> "image_processing"
        DigitalInkRecognitionScreen::class-> "ink_recognitions"

        else -> throw IllegalArgumentException("Unknown screen class: ${T::class}")
    }

    composable(route) { entry ->
        content(entry)
    }
}